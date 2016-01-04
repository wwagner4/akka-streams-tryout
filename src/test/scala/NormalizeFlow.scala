import akka.stream.scaladsl._
import scala.concurrent._
import akka.stream.FlowShape

object NormalizeFlow {

  import GraphDSL.Implicits._

  // Transforms a stream of integers to their sum
  protected val maxFlow: Flow[Int, Int, Future[Int]] = {
    val maxSink = Sink.fold[Int, Int](0)((cuml, elem) => cuml.max(elem))
    Flow.fromGraph {

      GraphDSL.create(maxSink) { implicit b =>
        fold =>
          FlowShape(fold.in, b.materializedValue.mapAsync(4)(identity).outlet)
      }
    }
  }

  def create: Flow[Int, Double, _] = {

    Flow.fromGraph {

      GraphDSL.create() { implicit b =>
        val bcast = b.add(Broadcast[Int](2))
        val zip = b.add(Zip[Int, Int]())
        val max = b.add(maxFlow)
        val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
        val norm = b.add(Flow[(Int, Int)].map { case (v, m) => v.toDouble / m })

        bcast ~> zip.in0
        bcast ~> max ~> fill ~> zip.in1
        zip.out ~> norm

        FlowShape(bcast.in, norm.outlet)
      }
    }
  }

}
