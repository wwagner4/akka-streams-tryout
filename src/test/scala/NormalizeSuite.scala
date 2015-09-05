
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class NormalizeSuite extends FunSuite {


  def randomIntegersSource(size: Int): Source[Int, _] = {
    val ran = new java.util.Random(28347928347L)
    val iter = Iterator.continually(ran.nextInt(101))
    Source(() => iter.take(size))
  }

  test("normalize a stream of integers") {
    def test(m: Materializer): Future[_] = {
      implicit val materializer = m

      val src: Source[Int, _] = randomIntegersSource(size = 20)

      // Converts a stream of positive integers to doubles ranging from 0 to 1.
      // The greatest input value converts to 1
      val normalizeFlow: Flow[Int, Double, _] = BufferingNormalizeFlow.create
      src.via(normalizeFlow).runForeach { norm => println("%.3f" format norm) }
    }
    runTest(test)
  }


  test("normalize a source of integers without buffering") {
    import FlowGraph.Implicits._

    def normalize(in: Source[Int, _]): Source[(Int, Int, Double), _] = {

      def fill[T](src: Source[T, _]) = src.map(r => Source.repeat(r)).flatten(FlattenStrategy.concat)
      def normalize: Flow[(Int, Int), (Int, Int, Double), _] = Flow[(Int, Int)].map {
        case (n, max) => (n, max, n.toDouble / max)
      }

      val maxSrc: Source[Int, _] = in.fold(0){(currMax, n) => if (n > 0) n.max(currMax) else currMax}
      val maxFill: Source[Int, _] = fill(maxSrc)

      // Create the final source using a flow that combines the prior constructs
      Source(in, maxFill)((mat, _) => mat) {

        implicit b => (in, maxFill) =>

          val zip = b.add(Zip[Int, Int]())
          val norm = b.add(normalize)

          in ~> zip.in0
          maxFill ~> zip.in1
          zip.out ~> norm

          norm.outlet
      }
    }


    def test(m: Materializer): Future[_] = {
      implicit val materializer = m

      val src = randomIntegersSource(size = 20000)
      val nsrc: Source[(Int, Int, Double), _] = normalize(src)

      var cnt = 0
      nsrc.runForeach {
        case (n, max, norm) =>
          cnt += 1
          if (cnt % 1000 == 0) println("NO BUFFER %7d %3d - %3d - %.3f" format(cnt, n, max, norm))
      }
    }
    runTest(test)
  }

  protected def runTest(f: Materializer => Future[_]) = {
    implicit val sys = ActorSystem("sys")
    try {
      val m = ActorMaterializer()
      Await.result(f(m), 2.second)
    } finally {
      sys.shutdown()
    }
  }

}