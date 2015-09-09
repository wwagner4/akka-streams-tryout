import akka.stream.scaladsl._

object NonBufferingNormalizer1 {

  import FlowGraph.Implicits._

  def normalize[I, F, O](in: Source[I, _],
                fold: (Source[I, _]) => Source[F, _],
                normalize: Flow[(I, F), O, _]): Source[O, _] = {

    def fill[T](src: Source[T, _]) = src.map(r => Source.repeat(r)).flatten(FlattenStrategy.concat)

    val maxSrc: Source[F, _] = fold(in)
    val maxFill: Source[F, _] = fill(maxSrc)

    // Create the final source using a flow that combines the prior constructs
    Source(in, maxFill)((mat, _) => mat) {

      implicit b => (in, maxFill) =>

        val zip = b.add(Zip[I, F]())
        val norm = b.add(normalize)

        in ~> zip.in0
        maxFill ~> zip.in1
        zip.out ~> norm

        norm.outlet
    }
  }


}
