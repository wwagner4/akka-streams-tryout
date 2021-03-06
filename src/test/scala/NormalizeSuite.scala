
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class NormalizeSuite extends FunSuite {

  // A source always generating the same stream of random values
  def randomIntegersSource(size: Int): Source[Int, _] = {
    val ran = new java.util.Random(283479287L)
    val iter = Iterator.continually(ran.nextInt(101))
    Source.fromIterator(() => iter.take(size))
  }

  test("normalize a stream of integers") {
    intercept[java.util.concurrent.TimeoutException] {
      withMaterializer { implicit m: Materializer =>

        val src: Source[Int, _] = randomIntegersSource(size = 10)

        // Converts a stream of positive integers to doubles ranging from 0 to 1.
        // The greatest input value converts to 1
        val normalizeFlow: Flow[Int, Double, _] = NormalizeFlow.create
        src.via(normalizeFlow).runForeach { norm => println("SIMPLE %.3f" format norm) }
      }
    }
  }

  test("normalize a stream of integers generic") {
    withMaterializer { implicit m: Materializer =>

      val src: Source[Int, _] = randomIntegersSource(size = 10)

      val cuml = new ZipWithCumulated[Int, Int]() {
        def bufferSize = 1000
        def start = 0
        def cumulate = (c: Int, e: Int) => math.max(c, e)
      }.create

      src
        .via(cuml)
        .map { case (value, max) => (value, value.toDouble / max) }
        .runForeach { case (value, norm) => println("GENERIC normalized %6d %-6.2f" format (value, norm)) }
    }
  }

  test("normalize a stream of integers with buffer") {
    withMaterializer { implicit m: Materializer =>

      val src: Source[Int, _] = randomIntegersSource(size = 10)

      // Converts a stream of positive integers to doubles ranging from 0 to 1.
      // The greatest input value converts to 1
      val normalizeFlow: Flow[Int, Double, _] = BufferingNormalizeFlow.create
      src.via(normalizeFlow).runForeach { norm => println("WITH BUFFER %.3f" format norm) }
    }
  }

  test("normalize a source of integers without buffering") {

    withMaterializer { implicit m: Materializer =>

      val src = randomIntegersSource(size = 20000)
      val nsrc: Source[Double, _] = NonBufferingNormalizer.normalize(src)

      var cnt = 0
      nsrc.runForeach { norm =>
        cnt += 1
        if (cnt % 1000 == 0) println("NO BUFFER A %7d %.3f" format (cnt, norm))
      }
    }
  }

  test("normalize a source of integers without buffering and general normalizer") {

    withMaterializer { implicit m: Materializer =>

      val src = randomIntegersSource(size = 20000)
      val fold = (in: Source[Int, _]) => in.fold(0) { (currMax, n) => n.max(currMax) }
      val norm = Flow[(Int, Int)].map { case (n, max) => n.toDouble / max }

      val nsrc: Source[Double, _] = NonBufferingNormalizer1.normalize(src, fold, norm)

      var cnt = 0
      nsrc.runForeach { norm =>
        cnt += 1
        if (cnt % 1000 == 0) println("NO BUFFER B %7d %.3f" format (cnt, norm))
      }
    }
  }

  protected def withMaterializer(f: Materializer => Future[_]) = {
    implicit val sys = ActorSystem("sys")
    try {
      val m = ActorMaterializer()
      Await.result(f(m), 2.second)
    } finally {
      sys.shutdown()
    }
  }

}