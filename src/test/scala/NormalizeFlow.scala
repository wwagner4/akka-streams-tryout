import akka.stream.scaladsl._
import scala.concurrent._

object NormalizeFlow {

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

  def create: Flow[Int, Double, _] = {

    Flow() { implicit b =>
      import FlowGraph.Implicits._
      val bcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, Int]())
      val max = b.add(maxFlow)
      val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
      val norm = b.add(Flow[(Int, Int)].map { case (v, m) => v.toDouble / m })

      bcast ~> zip.in0
      bcast ~> max ~> fill ~> zip.in1
      zip.out ~> norm

      (bcast.in, norm.outlet)
    }
  }

}
