
import org.scalatest._
import akka.stream.scaladsl._
import akka.stream._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import akka.stream.stage._

class NormalizeSuite extends FunSuite with BeforeAndAfterEach {

  // Create a stream of random integers with the size STREAM_SIZE
  def src(maxSize: Int): Source[Int, Unit] = Source(() => new Iterator[Int] {
    var cnt = 0
    def hasNext = cnt < maxSize
    def next = { cnt += 1; (math.random * 1000).toInt }
  })

  // Transforms a stream of integers to their sum
  val maxFlow: Flow[Int, Int, Future[Int]] = {
    import FlowGraph.Implicits._
    val maxSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((cuml, elem) => if (elem > cuml) elem else cuml)
    Flow(maxSink) {
      implicit builder =>
        fold =>
          (fold.inlet, builder.materializedValue.mapAsync(4)(identity).outlet)
    }
  }

  // Takes the first element of a stream and transforms it into an endless stream of that element.
  class Fill[A]() extends StatefulStage[A, A] {
    override def initial: StageState[A, A] =
      new StageState[A, A] {
        override def onPush(elem: A, ctx: Context[A]): SyncDirective = {
          val iter = new Iterator[A] {
            def hasNext = true;
            def next = elem
          }
          emit(iter, ctx)
        }
      }
  }

  val normFlow: Flow[Int, (Int, Double), Unit] = {

    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val bcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, Int]())
      val max = b.add(maxFlow)
      val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
      val norm = b.add(Flow[(Int, Int)].map { case (value, max) => (value, value.toDouble / max) })
      val buffer = b.add(Flow[Int].buffer(1600, OverflowStrategy.fail))

      bcast ~> buffer ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1
      zip.out ~> norm

      (bcast.in, norm.outlet)
    }
  }

  val STREAM_SIZE = 600
  test("normalie a stream of size %d" format (STREAM_SIZE)) {

    implicit val sys = ActorSystem("sys")
    try {
      implicit val materializer = ActorMaterializer()

      var cnt = 1
      val f = src(STREAM_SIZE).via(normFlow).runForeach {
        case (value, norm) =>
          println("%5d: %3d -> %.3f" format (cnt, value, norm))
          cnt += 1
      }

      Await.result(f, 2.second)
    } finally {
      sys.shutdown()
    }

  }

}