import akka.stream.scaladsl._
import akka.stream._
import scala.concurrent._
import akka.stream.stage._

trait GenericBufferingNormalizeFlow[A, B] {
  
  import FlowGraph.Implicits._
  
  def bufferSize: Int
  
  def start: B
  
  def combine: (B, A) => B

  /**
   * Combines all the input values to one output value
   */
  val foldFlow: Flow[A, B, Future[B]] = {
    val maxSink: Sink[A, Future[B]] = Sink.fold[B, A](start)(combine)
      
    Flow(maxSink) {
      implicit builder =>
        fold =>
          (fold.inlet, builder.materializedValue.mapAsync(4)(identity).outlet)
    }

  }

  def create: Flow[A, (A, B), _] = {

    Flow() { implicit b =>
      val bcast = b.add(Broadcast[A](2))
      val zip = b.add(Zip[A, B]())
      val max = b.add(foldFlow)
      val fill = b.add(Flow[B].transform(() => new Fill[B]()))
      val buffer = b.add(Flow[A].buffer(bufferSize, OverflowStrategy.fail))

      bcast ~> buffer ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1

      (bcast.in, zip.out)
    }
  }

}
