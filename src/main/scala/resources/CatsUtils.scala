package resources

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext

object CatsUtils {
  val ec = ExecutionContext.global
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

  def runIt[T](p: IO[T]): Option[T] = {
    try {
      Some(p.unsafeRunSync())
    } catch {
      case ex: Exception =>
        println(ex)
        None
    }
  }
}
