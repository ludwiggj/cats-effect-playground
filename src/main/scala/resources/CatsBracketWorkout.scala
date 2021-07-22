package resources

import cats.effect.{ExitCode, IO}
import resources.CatsUtils.{contextShift, runIt, timer}

import scala.concurrent.duration.DurationInt

class SomeAwsSdkJavaClient {
  println("Opening connections")

  def use(): Unit = println("Running logic")

  def close(): Unit = println("Closing connections")
}

object CatsBracketWorkout {
  def businessLogic(client: SomeAwsSdkJavaClient): IO[Unit] =
    for {
      _ <- IO(client.use())
    } yield ()

  def businessLogicWithError(client: SomeAwsSdkJavaClient): IO[Unit] =
    for {
      _ <- IO.raiseError(new RuntimeException("kaboom"))
      _ <- IO(client.use())
    } yield ()

  def runProgram(): Unit = {
    // we should probably use Blocker here, but let's forget about that detail for now
    def program: IO[Unit] = {
      for {
        client <- IO(new SomeAwsSdkJavaClient)
        _ <- businessLogic(client)
        _ <- IO(client.close())
      } yield ()
    }

    println("runProgram")
    runIt(program)
  }

  def runProgramHandleError(): Unit = {
    // we should probably use Blocker here, but let's forget about that detail for now
    def program: IO[Unit] = {
      for {
        client <- IO(new SomeAwsSdkJavaClient)
        e <- businessLogicWithError(client).attempt
        _ <- IO(client.close())
        _ <- IO.fromEither(e)
      } yield ()
    }

    println("\nrunProgramHandleError")
    runIt(program)
  }

  def runProgramHandleTimeout(): Unit = {
    // we should probably use Blocker here, but let's forget about that detail for now
    def program: IO[ExitCode] = {
      (for {
        client <- IO(new SomeAwsSdkJavaClient)
        _ <- IO.sleep(5.seconds)
        e <- businessLogic(client).attempt
        _ <- IO(client.close())
        _ <- IO.fromEither(e)
      } yield ())
        .map(_ => ExitCode.Success)
        .timeout(2.seconds)
    }

    println("\nrunProgramHandleTimeout")
    runIt(program)
  }

  // trait Bracket[F[_], E] extends MonadError[F, E] {
  //   def bracket[A, B](acquire: F[A])
  //                    (use: A => F[B])
  //                    (release: A => F[Unit]): F[B]
  // }

  def runProgramBracket(): Unit = {
    def program: IO[Unit] = IO(new SomeAwsSdkJavaClient).bracket(businessLogic)(client => IO(client.close()))

    println("\nrunProgramBracket")
    runIt(program)
  }

  def runProgramBracketHandleError(): Unit = {
    def program: IO[Unit] = IO(new SomeAwsSdkJavaClient).bracket(businessLogicWithError)(client => IO(client.close()))

    println("\nrunProgramBracketHandleError")
    runIt(program)
  }

  def runProgramBracketHandleTimeout(): Unit = {
    def program: IO[ExitCode] =
      IO(new SomeAwsSdkJavaClient)
        .bracket(_ => IO.sleep(5.seconds))(client => IO(client.close()))
        .map(_ => ExitCode.Success)
        .timeout(1.second)


    println("\nrunProgramBracketHandleTimeout")
    runIt(program)
  }

  def runBiggerProgram(): Unit = {
    def program: IO[Unit] =
      IO(DB())
        .bracket { db =>
          IO(SqsConsumer())
            .bracket { sqsClient =>
              IO(println("Using db and sqs"))
            }(sqsClient => IO(sqsClient.close()))
        }(dbClient => IO(dbClient.close()))

    println("\nrunBiggerProgram")
    runIt(program)
  }

  def main(args: Array[String]): Unit = {
    runProgram()
    runProgramHandleError()
    runProgramHandleTimeout()
    runProgramBracket()
    runProgramBracketHandleError()
    runProgramBracketHandleTimeout()
    runBiggerProgram()
  }
}
