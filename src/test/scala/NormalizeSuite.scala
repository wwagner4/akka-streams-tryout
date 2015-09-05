
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
    withMaterializer { m: Materializer =>
      implicit val materializer = m

      val src: Source[Int, _] = randomIntegersSource(size = 20)

      // Converts a stream of positive integers to doubles ranging from 0 to 1.
      // The greatest input value converts to 1
      val normalizeFlow: Flow[Int, Double, _] = BufferingNormalizeFlow.create
      src.via(normalizeFlow).runForeach { norm => println("WITH BUFFER %.3f" format norm) }
    }
  }

  test("normalize a source of integers without buffering") {

    withMaterializer { m: Materializer =>
      implicit val materializer = m

      val src = randomIntegersSource(size = 20000)
      val nsrc: Source[(Int, Int, Double), _] = NonBufferingNormalizer.normalize(src)

      var cnt = 0
      nsrc.runForeach {
        case (n, max, norm) =>
          cnt += 1
          if (cnt % 1000 == 0) println("NO BUFFER %7d %3d - %3d - %.3f" format(cnt, n, max, norm))
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