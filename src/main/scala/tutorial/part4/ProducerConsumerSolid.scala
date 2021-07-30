package tutorial.part4

import cats.effect._
import cats.effect.std.Console
import cats.syntax.all._
import cats.instances.list._

import scala.collection.immutable.Queue

case class State[F[_], A](queue: Queue[A], takers: Queue[Deferred[F, A]])

object State {
  def empty[F[_], A]: State[F, A] = State(Queue.empty, Queue.empty)
}

object ProducerConsumerSolid extends IOApp {
  val noOfConsumers = 9
  val noOfProducers = 9
  val consumerReportCount = 1000
  val producerReportCount = 100000

  // Consumer behaviour
  // (1) If queue is not empty, it will extract and return its head. The new state will keep the tail of the queue.
  //     No change to takers will be needed.
  // (2) If queue is empty it will use a new Deferred instance as a new taker, add it to the takers queue, and
  //     'block' the caller by invoking taker.get
  def consumer[F[_]: Async: Console](id: Int, stateR: Ref[F, State[F, Int]]): F[Unit] = {
    val take: F[Int] =
      Deferred[F, Int].flatMap { taker =>
        stateR.modify {
          // Got element in queue, we can just return it
          case State(queue, takers) if queue.nonEmpty =>
            val (i, rest) = queue.dequeue
            State(rest, takers) -> Async[F].pure(i)

          // No element in queue, must block caller until some is available
          case State(queue, takers) =>
            State(queue, takers.enqueue(taker)) -> taker.get
        }.flatten
      }

    for {
      i <- take
      _ <- if (i % consumerReportCount == 0) Console[F].println(s"Consumer $id has reached $i items") else Async[F].unit
      _ <- consumer(id, stateR)
    } yield ()
  }

  // Producer behaviour
  // (1) If there are waiting takers, it will take the first in the queue and offer it the newly produced
  //     element (taker.complete).
  // (2) If no takers are present, it will just enqueue the produced element.
  def producer[F[_]: Sync: Console](id: Int, counterR: Ref[F, Int], stateR: Ref[F, State[F, Int]]): F[Unit] = {
    def offer(i: Int): F[Unit] = {
      stateR.modify {
        case State(queue, takers) if takers.nonEmpty =>
          val (taker, rest) = takers.dequeue
          State(queue, rest) -> taker.complete(i).void

        case State(queue, takers) =>
          State(queue.enqueue(i), takers) -> Sync[F].unit
      }.flatten
    }

    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- offer(i)
      _ <- if (i % producerReportCount == 0) Console[F].println(s"Producer $id has reached $i items") else Sync[F].unit
      _ <- producer(id, counterR, stateR)
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      stateR <- Ref.of[IO, State[IO, Int]](State.empty[IO, Int])
      counterR <- Ref.of[IO, Int](1)
      producers = List.range(1, noOfProducers + 1).map(producer(_, counterR, stateR))
      consumers = List.range(1, noOfConsumers + 1).map(consumer(_, stateR))
      res <- (producers ++ consumers)
        .parSequence
        // Run producers and consumers in parallel until done (likely by user cancelling with CTRL-C)
        .as(ExitCode.Success)
        .handleErrorWith { t =>
          Console[IO].errorln(s"Error caught: ${t.getMessage}").as(ExitCode.Error)
        }
    } yield res
}