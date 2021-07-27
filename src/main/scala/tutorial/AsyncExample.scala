package tutorial

import cats.effect.{IO, IOApp}
import java.util.concurrent.{Executors, TimeUnit}

object AsyncExample extends IOApp.Simple {
  private val scheduler = Executors.newScheduledThreadPool(1)

  // The cool thing about async is that there is no way for the caller to tell the difference
  // between program1 and program2 - they both appear synchronous and in program2 the fiber
  // calling async_ will be suspended until the cb is invoked so no cpu resource is wasted
  // waiting
  private val program1: IO[Unit] = IO.async_[String] { cb =>
    cb(Right("Prints straight away"))
  }.map(println)

  private val program2: IO[Unit] = IO.async_[String] { cb =>
    scheduler.schedule(new Runnable {
      def run(): Unit = cb(Right("Prints after 2 second delay"))
    }, 2, TimeUnit.SECONDS)
  }.map(println)

  private val program3: IO[Unit] = IO.async_[String] { cb =>
    scheduler.schedule(new Runnable {
      def run(): Unit = cb(Left(new RuntimeException("Crash and burn")))
    }, 2, TimeUnit.SECONDS)
  }.map(println)

  // In this case the exception thrown by program 3 will mean that the scheduler is not shutdown
  override def run: IO[Unit] =
    program1 *>
    program2 *>
    program3 *>
    IO(scheduler.shutdown())
}