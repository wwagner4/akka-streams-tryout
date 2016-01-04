import akka.stream.scaladsl._
import akka.stream._
import scala.concurrent._
import akka.stream.stage._

trait ZipWithCumulated[E, C] {

  import FlowGraph.Implicits._

  def bufferSize: Int

  def start: C

  def cumulate: (C, E) => C

  /**
   * Combines all the input values to one output value
   */
  val foldFlow: Flow[E, C, Future[C]] = {
    val cumlSink: Sink[E, Future[C]] = Sink.fold[C, E](start)(cumulate)
    Flow(cumlSink) {
      implicit builder =>
          val out = builder.materializedValue.mapAsync(4)(identity).outlet
          fold => (fold.inlet, out)
    }
  }

  def apply(): Flow[E, (E, C), _] = {

    Flow() { implicit b =>
      val bcast = b.add(Broadcast[E](2))
      val zip = b.add(Zip[E, C]())
      val max = b.add(foldFlow)
      val fill = b.add(Flow[C].transform(() => new Fill[C]()))
      val buffer = b.add(Flow[E].buffer(bufferSize, OverflowStrategy.fail))

      bcast ~> buffer ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1

      (bcast.in, zip.out)
    }
  }

}
