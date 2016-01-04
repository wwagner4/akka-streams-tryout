import akka.stream.scaladsl._
import akka.stream.SourceShape

object NonBufferingNormalizer1 {

  import GraphDSL.Implicits._

  def normalize[I, F, O](in: Source[I, _],
                         fold: (Source[I, _]) => Source[F, _],
                         normalize: Flow[(I, F), O, _]): Source[O, _] = {

    def fill[T](src: Source[T, _]) = src.map(r => Source.repeat(r)).flatMapConcat(identity)

    val folded: Source[F, _] = fold(in)
    val foldedFill: Source[F, _] = fill(folded)

    // Create the final source using a flow that combines the prior constructs
    Source.fromGraph {
      GraphDSL.create(in) {

        implicit b =>
          in =>

            val zip = b.add(Zip[I, F]())
            val norm = b.add(normalize)

            in ~> zip.in0
            foldedFill ~> zip.in1
            zip.out ~> norm

            SourceShape(norm.outlet)
      }
    }
  }

}
