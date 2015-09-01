
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom

class NormalizeSuite extends FunSuite with BeforeAndAfterEach {

  
  def randomIntegersSource(size: Int): Source[Int, _] = {
    val iter = Iterator.continually(ThreadLocalRandom.current().nextInt(100))
    Source(() => iter.take(size))
  }
  
  test("normalize a stream of integers") {
    def test(m: Materializer): Option[Future[_]] = {
      implicit val materializer = m

      val src: Source[Int, _] = randomIntegersSource(size = 20)
      
      // Converts a stream of positive integers to doubles ranging from 0 to 1.
      // The greatest input value converts to 1
      val normalizeFlow: Flow[Int, Double, _] = BufferingNormalizeFlow.create
      Some(src.via(normalizeFlow).runForeach {norm => println("%.3f" format norm)})
    }
    runTest(test)
  }

  
  trait SourceContainer[T, M] {
    def source: Source[T, M]
  }
  
  def twiceMaterializingFlow: Flow[SourceContainer[Int, _], Double, _] = ???
  
  test("normalize a stream of source containers") {
    def test(m: Materializer): Option[Future[_]] = {
      implicit val materializer = m

      val cont: SourceContainer[Int, _] = new SourceContainer[Int, Any] {
        def source = randomIntegersSource(size = 20)
      }
      val src = Source.single(cont)
      
      // Converts a stream of sources of positive integers to doubles ranging from 0 to 1.
      // The greatest input value converts to 1
      val normalizeFlow: Flow[SourceContainer[Int, _], Double, _] = twiceMaterializingFlow
      Some(src.via(normalizeFlow).runForeach {norm => println("%.3f" format norm)})
    }
    runTest(test)
  }
  
  protected def runTest(f: Materializer => Option[Future[_]]) = {
    implicit val sys = ActorSystem("sys")
    try {
      val m = ActorMaterializer()
      f(m) match {
        case Some(future) => Await.result(future, 2.second)
        case None => ()
      }
    } finally {
      sys.shutdown()
    }
  }

}