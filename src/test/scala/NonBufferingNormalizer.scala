import akka.stream.scaladsl._

object NonBufferingNormalizer {

  import FlowGraph.Implicits._

  def normalize(in: Source[Int, _]): Source[(Int, Int, Double), _] = {

    def fill[T](src: Source[T, _]) = src.map(r => Source.repeat(r)).flatten(FlattenStrategy.concat)
    def normalize: Flow[(Int, Int), (Int, Int, Double), _] = Flow[(Int, Int)].map {
      case (n, max) => (n, max, n.toDouble / max)
    }

    val maxSrc: Source[Int, _] = in.fold(0) { (currMax, n) => if (n > 0) n.max(currMax) else currMax }
    val maxFill: Source[Int, _] = fill(maxSrc)

    // Create the final source using a flow that combines the prior constructs
    Source(in, maxFill)((mat, _) => mat) {

      implicit b => (in, maxFill) =>

        val zip = b.add(Zip[Int, Int]())
        val norm = b.add(normalize)

        in ~> zip.in0
        maxFill ~> zip.in1
        zip.out ~> norm

        norm.outlet
    }
  }


}
