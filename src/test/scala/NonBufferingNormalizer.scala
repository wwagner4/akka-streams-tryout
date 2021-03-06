import akka.stream.scaladsl._
import akka.stream.SourceShape

object NonBufferingNormalizer {

  import GraphDSL.Implicits._

  def normalize(in: Source[Int, _]): Source[Double, _] = {

    def fill[T](src: Source[T, _]) = src.map(r => Source.repeat(r)).flatMapConcat(identity)
    def normalize: Flow[(Int, Int), Double, _] = Flow[(Int, Int)].map {
      case (n, max) => n.toDouble / max
    }

    val maxSrc: Source[Int, _] = in.fold(0) { (currMax, n) => n.max(currMax) }
    val maxFill: Source[Int, _] = fill(maxSrc)

    // Create the final source using a flow that combines the prior constructs
    Source.fromGraph {
      GraphDSL.create(in, maxFill)((mat, _) => mat) {
        implicit b =>
          (in, maxFill) =>

            val zip = b.add(Zip[Int, Int]())
            val norm = b.add(normalize)

            in ~> zip.in0
            maxFill ~> zip.in1
            zip.out ~> norm

            SourceShape(norm.outlet)
      }
    }
  }

}
