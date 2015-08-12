
import org.scalatest._
import akka.stream.scaladsl._
import akka.stream._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import akka.stream.stage._
import scala.concurrent.forkjoin.ThreadLocalRandom

class NormalizeSuite extends FunSuite with BeforeAndAfterEach {

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

  val bufferingNormalizeFlow: Flow[Int, Double, _] = {

    val BUFFER_SIZE = 1600

    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val bcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, Int]())
      val max = b.add(maxFlow)
      val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
      val norm = b.add(Flow[(Int, Int)].map { case (value, max) => value.toDouble / max })
      val buffer = b.add(Flow[Int].buffer(BUFFER_SIZE, OverflowStrategy.fail))

      bcast ~> buffer ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1
      zip.out ~> norm

      (bcast.in, norm.outlet)
    }
  }
  
  def randomIntegersSource(size: Int): Source[Int, _] = {
    val iter = Iterator.continually(ThreadLocalRandom.current().nextInt(100))
    Source(() => iter.take(size))
  }
  
  test("normalize a stream of integers") {

    implicit val sys = ActorSystem("sys")
    try {
      implicit val materializer = ActorMaterializer()

      val src: Source[Int, _] = randomIntegersSource(size = 20)
      
      // Converts a stream of positive integers to doubles ranging from 0 to 1.
      // The gratest input value converts to 1
      val normalizeFlow: Flow[Int, Double, _] = bufferingNormalizeFlow
      
      
      val f = src.via(normalizeFlow).runForeach {norm => println("%.3f" format norm)}

      Await.result(f, 2.second)
    } finally {
      sys.shutdown()
    }

  }

}