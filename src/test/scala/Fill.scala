import akka.stream.stage.{SyncDirective, Context, StageState, StatefulStage}

// Takes the first element of a stream and transforms it into an endless stream of that element.
class Fill[A]() extends StatefulStage[A, A] {
  override def initial: StageState[A, A] =
    new StageState[A, A] {
      override def onPush(elem: A, ctx: Context[A]): SyncDirective = {
        val iter = new Iterator[A] {
          def hasNext = true

          def next() = elem
        }
        emit(iter, ctx)
      }
    }
}

