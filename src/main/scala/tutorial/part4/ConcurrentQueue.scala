package tutorial.part4

import cats.effect._
import cats.effect.implicits.monadCancelOps_
import cats.effect.std.Console
import cats.instances.list._
import cats.syntax.all._

class ConcurrentQueue[F[_] : Async, A](stateR: Ref[F, BoundedState[F, A]]) {
  val take: F[A] =
    Deferred[F, A].flatMap { taker =>
      Async[F].uncancelable { poll =>
        stateR.modify {
          // Got element in queue, we can just return it
          case BoundedState(queue, capacity, takers, offerers) if queue.nonEmpty && offerers.isEmpty =>
            val (a, rest) = queue.dequeue
            BoundedState(rest, capacity, takers, offerers) -> Async[F].pure(a)

          case BoundedState(queue, capacity, takers, offerers) if queue.nonEmpty =>
            val (a, restQueue) = queue.dequeue
            val ((offered, offerer), restOfferers) = offerers.dequeue
            BoundedState(restQueue.enqueue(offered), capacity, takers, restOfferers) -> offerer.complete(()).as(a)

          case BoundedState(queue, capacity, takers, offerers) if queue.isEmpty && offerers.isEmpty =>
            val cleanup = stateR.update {
              s => s.copy(takers = s.takers.filter(_ ne taker))
            }
            BoundedState(queue, capacity, takers.enqueue(taker), offerers) -> poll(taker.get).onCancel(cleanup)

          case BoundedState(queue, capacity, takers, offerers) =>
            val ((offered, offerer), restOfferers) = offerers.dequeue
            BoundedState(queue, capacity, takers, restOfferers) -> offerer.complete(()).as(offered)
        }.flatten
      }
    }

  def offer(a: A): F[Unit] = {
    Deferred[F, Unit].flatMap[Unit] { offerer =>
      Async[F].uncancelable { poll =>
        stateR.modify {
          case BoundedState(queue, capacity, takers, offerers) if takers.nonEmpty =>
            val (taker, rest) = takers.dequeue
            BoundedState(queue, capacity, rest, offerers) -> taker.complete(a).void

          case BoundedState(queue, capacity, takers, offerers) if queue.size < capacity =>
            BoundedState(queue.enqueue(a), capacity, takers, offerers) -> Async[F].unit

          case BoundedState(queue, capacity, takers, offerers) =>
            // Must take care of cleaning up the state if there is a cancellation. The cleaning up will have to
            // remove the offerer from the list of offerers kept in the state, as it shall never be completed.
            val cleanup = stateR.update {
              s => s.copy(offerers = s.offerers.filter(_._2 ne offerer))
            }
            BoundedState(queue, capacity, takers, offerers.enqueue(a -> offerer)) -> poll(offerer.get).onCancel(cleanup)
        }.flatten
      }
    }
  }
}

object ConcurrentQueue {
  def apply[F[_] : Async, A](capacity: Int): F[ConcurrentQueue[F, A]] = {
    val emptyState: BoundedState[F, A] = BoundedState.empty[F, A](capacity)
    val emptyStateRef: F[Ref[F, BoundedState[F, A]]] = Ref.of[F, BoundedState[F, A]](emptyState)
    emptyStateRef.map {
      st: Ref[F, BoundedState[F, A]] => new ConcurrentQueue(st)
    }
  }
}

object ConcurrentQueueWorkout extends IOApp {
  val noOfConsumers = 9
  val noOfProducers = 9
  val consumerReportCount = 10000
  val producerReportCount = 10000

  def consumer[F[_] : Async : Console, A](id: Int, queue: ConcurrentQueue[F, (Int, A)]): F[Unit] =
    for {
      head <- queue.take
      (i, a) = head
      _ <-
        if (i % consumerReportCount == 0)
          Console[F].println(s"Consumer $id has reached $i items, each $a")
        else
          Async[F].unit
      _ <- consumer(id, queue)
    } yield ()

  def producer[F[_] : Async : Console, A](id: Int,
                                          queue: ConcurrentQueue[F, (Int, A)],
                                          counterR: Ref[F, Int],
                                          a: A): F[Unit] =
    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- queue.offer(i -> a)
      _ <-
        if (i % producerReportCount == 0)
          Console[F].println(s"Producer $id has reached $i items, each $a")
        else
          Async[F].unit
      _ <- producer(id, queue, counterR, a)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      queue <- ConcurrentQueue[IO, (Int, String)](100)
      counterR <- Ref.of[IO, Int](1)
      producers = List.range(1, noOfProducers + 1).map(producer(_, queue, counterR, "hello"))
      consumers = List.range(1, noOfConsumers + 1).map(consumer(_, queue))
      res <- (producers ++ consumers)
        .parSequence
        // Run producers and consumers in parallel until done (likely by user cancelling with CTRL-C)
        .as(ExitCode.Success)
        .handleErrorWith {
          t =>
            Console[IO].errorln(s"Error caught: ${
              t.getMessage
            }").as(ExitCode.Error)
        }
    } yield res
  }
}