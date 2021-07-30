package tutorial.part1

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.DurationInt

object HelloWorld extends IOApp.Simple {
  def hello1: IO[Unit] = IO.println("Hello, World!")

  def hello2: IO[Unit] = IO.println("Hello") flatMap { _ =>
    IO.println("World! (flatMap)")
  }

  def hello3: IO[Unit] = for {
    _ <- IO.println("Hello")
    _ <- IO.println("World! (for)")
  } yield ()

  def hello4: IO[Unit] = IO.println("Hello") >> IO.println("World! (>>)")

  def hello5: IO[Unit] = IO.println("Hello") *> IO.println("World! (*>)")

  def cancelExample: IO[Unit] = {
    lazy val loop: IO[Unit] = IO.println("Hello, World!") >> loop
    loop.timeout(5.seconds)
  }

  val run: IO[Unit] = hello1 *> hello2 *> hello3 *> hello4 *> hello5 *> cancelExample
}
