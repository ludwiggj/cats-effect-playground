package resources

import cats.effect.unsafe.IORuntime
import cats.effect.IO

object CatsUtils {
  private implicit val ioRuntime: IORuntime = cats.effect.unsafe.implicits.global

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