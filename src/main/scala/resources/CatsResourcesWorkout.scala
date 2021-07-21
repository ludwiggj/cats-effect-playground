package resources

import cats.effect.{ExitCode, IO}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class SomeAwsSdkJavaClient {
  println("Opening connections")

  def use: Unit = println("Using")

  def close: Unit = println("Closing connections")
}


object CatsResourcesWorkout {
  def runProgramAndHandleErrors(): Unit = {
    def businessLogic(client: SomeAwsSdkJavaClient): IO[Unit] =
      for {
        _ <- IO.raiseError(new RuntimeException("kaboom"))
        _ <- IO(client.use)
      } yield ()

    // we should probably use Blocker here, but let's forget about that detail for now
    def program: IO[Unit] = {
      for {
        client <- IO(new SomeAwsSdkJavaClient)
        e      <- businessLogic(client).attempt
        _      <- IO(client.close)
        _      <- IO.fromEither(e)
      } yield ()
    }

    println("runProgramAndHandleErrors")
    try {
      program.unsafeRunSync()
    } catch {
      case ex: Exception => println(ex)
    }
  }

  def runCancellableProgram(): Unit = {
    val ec = ExecutionContext.global
    implicit val timer = IO.timer(ec)
    implicit val contextShift = IO.contextShift(ec)

    def businessLogic(client: SomeAwsSdkJavaClient): IO[Unit] =
      for {
        _ <- IO(client.use)
      } yield ()

    // we should probably use Blocker here, but let's forget about that detail for now
    def program: IO[Unit] = {
      for {
        client <- IO(new SomeAwsSdkJavaClient)
        _      <- IO.sleep(5.seconds)
        e      <- businessLogic(client).attempt
        _      <- IO(client.close)
        _      <- IO.fromEither(e)
      } yield ()
    }

    println("runCancellableProgram")
    program.timeout(2.seconds).map(_ => ExitCode.Success).unsafeRunSync()
  }

  def main(args: Array[String]): Unit = {
    runProgramAndHandleErrors()
    runCancellableProgram()
  }
}
