package tutorial

import cats.effect.{IO, IOApp, Resource}

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

object AsyncExampleWithResource extends IOApp.Simple {
  private val schedulerResource: Resource[IO, ScheduledExecutorService] = Resource.make(
    IO(Executors.newScheduledThreadPool(1))
  )(r => IO(r.shutdown()))

  private val program1: IO[Unit] = IO.async_[String] { cb =>
    cb(Right("Prints straight away"))
  }.map(println)

  private def program2(scheduler: ScheduledExecutorService): IO[Unit] = IO.async_[String] { cb =>
    scheduler.schedule(new Runnable {
      def run(): Unit = cb(Right("Prints after 3 second delay"))
    }, 3, TimeUnit.SECONDS)
  }.map(println)

  private def program3(scheduler: ScheduledExecutorService): IO[Unit] = IO.async_[String] { cb =>
    scheduler.schedule(new Runnable {
      def run(): Unit = cb(Left(new RuntimeException("Crash and burn after 1 second delay")))
    }, 1, TimeUnit.SECONDS)
  }.map(println)

  // In this case the exception thrown by program 3 will mean that the scheduler is not shutdown
  override def run: IO[Unit] =
    schedulerResource.use { r =>
      program1 *>
        program2(r) *>
        program3(r)
    }
}