import akka.stream.stage.DownstreamDirective
import akka.stream.stage.DetachedContext
import akka.stream.stage.UpstreamDirective
import akka.stream.stage.DetachedStage

class Fill[T] extends DetachedStage[T, T] {
  private var currentValue: T = _
  private var waitingFirstValue = true

  override def onPush(elem: T, ctx: DetachedContext[T]): UpstreamDirective = {
    currentValue = elem
    waitingFirstValue = false
    if (ctx.isHoldingDownstream) ctx.pushAndPull(currentValue)
    else ctx.pull()
  }

  override def onPull(ctx: DetachedContext[T]): DownstreamDirective = {
    if (waitingFirstValue) ctx.holdDownstream()
    else ctx.push(currentValue)
  }

}