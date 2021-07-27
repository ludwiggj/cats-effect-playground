package resources

import cats.effect.{ExitCode, IO, Resource}
import cats.implicits.catsSyntaxTuple2Semigroupal
import resources.CatsUtils.runIt

import scala.concurrent.duration.DurationInt

// Resource[F[_], A] encapsulates the logic to acquire and finalize a
// resource of type A and forms a Monad in A so that we can construct
// composite resources without the deep nesting of bracket.

// abstract class Resource[F[_], A] {
//  def use[B](f: A => F[B])(implicit F: Bracket[F, Throwable]): F[B]
// }

// object Resource {
//  def make[F[_], A](open: F[A])(close: A => F[Unit]): Resource[F, A]
// }

object CatsResourceWorkout {
  private val dbResource: Resource[IO, DB] = Resource.make(IO(DB()))(r => IO(r.close()))
  private val sqsResource: Resource[IO, SqsConsumer] = Resource.make(IO(SqsConsumer()))(r => IO(r.close()))

  def businessLogic(db: DB, sqs: SqsConsumer): IO[Unit] =
    for {
      _ <- IO(db.use())
      _ <- IO(sqs.use())
    } yield ()

  def businessLogicWithError(db: DB, sqs: SqsConsumer): IO[Unit] =
    for {
      _ <- IO.raiseError(new RuntimeException("kaboom"))
      _ <- IO(db.use())
      _ <- IO(sqs.use())
    } yield ()

  def businessLogicWithDelay(db: DB, sqs: SqsConsumer): IO[ExitCode] =
    (for {
      _ <- IO(db.use())
      _ <- IO.sleep(5.seconds)
      _ <- IO(sqs.use())
    } yield ExitCode.Success)
      .timeout(2.seconds)

  def program(f: (DB, SqsConsumer) => IO[_]): Resource[IO, Unit] = {
    for {
      db <- dbResource
      sqs <- sqsResource
      _ <- Resource.eval(f(db, sqs))
    } yield ()
  }

  def resources: Resource[IO, (DB, SqsConsumer)] = {
    for {
      db <- dbResource
      sqs <- sqsResource
    } yield (db, sqs)
  }

  def runProgramV1(): Unit = {
    println("\nrunProgramV1...")
    runIt(program(businessLogic).use(_ => IO.unit))
  }

  def runProgramV2(): Unit = {
    println("\nrunProgramV2...")
    runIt {
      resources.use {
        case (db, sqs) => businessLogic(db, sqs)
      }
    }
  }

  def runProgramV3(): Unit = {
    println("\nrunProgramV3...")
    runIt {
      // Can just combine the resources via tupled
      (dbResource, sqsResource).tupled.use {
        case (db, sqs) => businessLogic(db, sqs)
      }
    }
  }

  def runProgramHandleErrorV1(): Unit = {
    println("\nrunProgramHandleErrorV1...")
    runIt(program(businessLogicWithError).use(_ => IO.unit))
  }

  def runProgramHandleErrorV2(): Unit = {
    println("\nrunProgramHandleErrorV2...")
    runIt {
      resources.use {
        case (db, sqs) => businessLogicWithError(db, sqs)
      }
    }
  }

  def runProgramHandleTimeoutV1(): Unit = {
    println("\nrunProgramHandleTimeoutV1...")
    runIt(program(businessLogicWithDelay).use(_  => IO.unit))
  }

  def runProgramHandleTimeoutV2(): Unit = {
    println("\nrunProgramHandleTimeoutV2...")
    runIt {
      resources.use {
        case (db, sqs) => businessLogicWithDelay(db, sqs)
      }
    }
  }

  def runProgramHandleErroneousCloseV1(): Unit = {
    def program: Resource[IO, (DB, SqsConsumer)] = {
      for {
        db <- dbResource
        sqs <- Resource.make(IO(SqsConsumer()))(_ => IO.raiseError(new RuntimeException("closing... no wait... boom!")))
        _ <- Resource.eval(businessLogic(db, sqs))
      } yield (db, sqs)
    }

    println("\nrunProgramHandleErroneousCloseV1...")
    runIt(program.use(_  => IO.unit))
  }

  def runProgramHandleErroneousCloseV2(): Unit = {
    def resources: Resource[IO, (DB, SqsConsumer)] = {
      for {
        db <- dbResource
        sqs <- Resource.make(IO(SqsConsumer()))(_ => IO.raiseError(new RuntimeException("closing... no wait... boom!")))
      } yield (db, sqs)
    }

    println("\nrunProgramHandleErroneousCloseV2...")
    runIt {
      resources.use {
        case (db, sqs) => businessLogic(db, sqs)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    runProgramV1()
    runProgramV2()
    runProgramV3()
    runProgramHandleErrorV1()
    runProgramHandleErrorV2()
    runProgramHandleTimeoutV1()
    runProgramHandleTimeoutV2()
    runProgramHandleErroneousCloseV1()
    runProgramHandleErroneousCloseV2()
  }
}