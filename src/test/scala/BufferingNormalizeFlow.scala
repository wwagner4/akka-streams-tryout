import akka.stream.scaladsl._
import akka.stream._
import scala.concurrent._
import akka.stream.stage._

object BufferingNormalizeFlow {

  // Transforms a stream of integers to their sum
  protected val maxFlow: Flow[Int, Int, Future[Int]] = {
    import GraphDSL.Implicits._
    val maxSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((cuml, elem) =>
      if (elem > cuml) elem else cuml)
    Flow.fromGraph {
      GraphDSL.create(maxSink) {
        implicit builder =>
          fold =>
            FlowShape(fold.in, builder.materializedValue.mapAsync(4)(identity).outlet)
      }
    }
  }

  def create: Flow[Int, Double, _] = {

    val BUFFER_SIZE = 1600

    Flow.fromGraph {
      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val bcast = b.add(Broadcast[Int](2))
        val zip = b.add(Zip[Int, Int]())
        val max = b.add(maxFlow)
        val fill = b.add(Flow[Int].transform(() => new Fill[Int]()))
        val norm = b.add(Flow[(Int, Int)].map { case (v, m) => v.toDouble / m })
        val buffer = b.add(Flow[Int].buffer(BUFFER_SIZE, OverflowStrategy.fail))

        bcast ~> buffer ~> zip.in0
        bcast ~> max ~> fill ~> zip.in1
        zip.out ~> norm

        FlowShape(bcast.in, norm.outlet)
      }
    }
  }

}
