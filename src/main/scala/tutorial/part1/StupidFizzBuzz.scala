package tutorial.part1

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.DurationInt

// obviously this isn't actually the problem definition, but it's kinda fun
object StupidFizzBuzz extends IOApp.Simple {
  val run: IO[Unit] =
    for {
      // Ref is an asynchronous, concurrent mutable reference (wrapper over Atomic)
      ctr <- IO.ref(0)

      wait = IO.sleep(1.second) // IO.unit
      poll = wait *> ctr.get // IO[Int]

      // foreverM evaluates the current IO in an infinite loop, terminating only on error or cancellation.
      // start starts the execution of the source suspended in the IO context.
      _ <- poll.flatMap(IO.println(_)).foreverM.start
      _ <- poll.map(_ % 3 == 0).ifM(IO.println("fizz"), IO.unit).foreverM.start
      _ <- poll.map(_ % 5 == 0).ifM(IO.println("buzz"), IO.unit).foreverM.start

      _ <- (wait *> ctr.update(_ + 1)).foreverM.void
    } yield ()
}
