import akka.stream.scaladsl._
import akka.stream._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import akka.stream.stage._
import scala.concurrent.forkjoin.ThreadLocalRandom

object BufferingNormalizeFlow {

  // Transforms a stream of integers to their sum
  protected val maxFlow: Flow[Int, Int, Future[Int]] = {
    import FlowGraph.Implicits._
    val maxSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((cuml, elem) => if (elem > cuml) elem else cuml)
    Flow(maxSink) {
      implicit builder =>
        fold =>
          (fold.inlet, builder.materializedValue.mapAsync(4)(identity).outlet)
    }
  }

  // Takes the first element of a stream and transforms it into an endless stream of that element.
  protected class Fill[A]() extends StatefulStage[A, A] {
    override def initial: StageState[A, A] =
      new StageState[A, A] {
        override def onPush(elem: A, ctx: Context[A]): SyncDirective = {
          val iter = new Iterator[A] {
            def hasNext = true
            def next() = elem
          }
          emit(iter, ctx)
        }
      }
  }

  def create: Flow[Int, Double, _] = {

    val BUFFER_SIZE = 1600

    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val bcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, Int]())
      val max = b.add(maxFlow)
      val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
      val norm = b.add(Flow[(Int, Int)].map { case (v, m) => v.toDouble / m })
      val buffer = b.add(Flow[Int].buffer(BUFFER_SIZE, OverflowStrategy.fail))

      bcast ~> buffer ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1
      zip.out ~> norm

      (bcast.in, norm.outlet)
    }
  }

}
