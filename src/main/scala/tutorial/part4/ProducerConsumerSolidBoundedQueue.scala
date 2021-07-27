package tutorial.part4

import cats.effect._
import cats.effect.std.Console
import cats.syntax.all._
import cats.instances.list._

import scala.collection.immutable.Queue

case class BoundedState[F[_], A](queue: Queue[A], capacity: Int, takers: Queue[Deferred[F, A]], offerers: Queue[(A, Deferred[F, Unit])])

object BoundedState {
  def empty[F[_], A](capacity:Int): BoundedState[F, A] = BoundedState(Queue.empty, capacity, Queue.empty, Queue.empty)
}

object ProducerConsumerSolidBoundedQueue extends IOApp {
  val noOfConsumers = 9
  val noOfProducers = 9
  val consumerReportCount = 10000
  val producerReportCount = 10000

  // Consumer behaviour
  // (1) If queue is not empty:
  //     (a) If offerers is empty then it will extract and return queue's head.
  //     (b) If offerers is not empty (there is some producer waiting) then things are more complicated. The queue head
  //         will be returned to the consumer. Now we have a free bucket available in queue. So the first waiting
  //         offerer can use that bucket to add the element it offers. That element will be added to queue, and the
  //         Deferred instance will be completed so the producer is released (unblocked).
  // (2) If queue is empty:
  //     (a) If offerers is empty then there is nothing we can give to the caller, so a new taker is created and added
  //         to takers while caller is blocked with taker.get.
  //     (b) If offerers is not empty then the first offerer in queue is extracted, its Deferred instance released while
  //         the offered element is returned to the caller.
  def consumer[F[_] : Async : Console](id: Int, stateR: Ref[F, BoundedState[F, Int]]): F[Unit] = {
    val take: F[Int] =
      Deferred[F, Int].flatMap { taker =>
        stateR.modify {
          // Got element in queue, we can just return it
          case BoundedState(queue, capacity, takers, offerers) if queue.nonEmpty && offerers.isEmpty =>
            val (i, rest) = queue.dequeue
            BoundedState(rest, capacity, takers, offerers) -> Async[F].pure(i)

          case BoundedState(queue, capacity, takers, offerers) if queue.nonEmpty =>
            val (i, restQueue) = queue.dequeue
            val ((offered, offerer), restOfferers) = offerers.dequeue
            BoundedState(restQueue.enqueue(offered), capacity, takers, restOfferers) -> offerer.complete(()).as(i)

          case BoundedState(queue, capacity, takers, offerers) if queue.isEmpty && offerers.isEmpty =>
            BoundedState(queue, capacity, takers.enqueue(taker), offerers) -> taker.get

          case BoundedState(queue, capacity, takers, offerers) =>
            val ((offered, offerer), restOfferers) = offerers.dequeue
            BoundedState(queue, capacity, takers, restOfferers) -> offerer.complete(()).as(offered)
        }.flatten
      }

    for {
      i <- take
      _ <- if (i % consumerReportCount == 0) Console[F].println(s"Consumer $id has reached $i items") else Async[F].unit
      _ <- consumer(id, stateR)
    } yield ()
  }

  // Producer behaviour
  // (1) If there is any waiting taker then the produced element will be passed to it, releasing the blocked fiber.
  // (2) If there is no waiting taker but queue is not full, then the offered element will be enqueued there.
  // (3) If there is no waiting taker and queue is already full then a new offerer is created, blocking the producer
  //     fiber on the .get method of the Deferred instance.
  def producer[F[_] : Async : Console](id: Int, counterR: Ref[F, Int], stateR: Ref[F, BoundedState[F, Int]]): F[Unit] = {
    def offer(i: Int): F[Unit] = {
      Deferred[F, Unit].flatMap[Unit] { offerer =>
        stateR.modify {
          case BoundedState(queue, capacity, takers, offerers) if takers.nonEmpty =>
            val (taker, rest) = takers.dequeue
            BoundedState(queue, capacity, rest, offerers) -> taker.complete(i).void

          case BoundedState(queue, capacity, takers, offerers) if queue.size < capacity =>
            BoundedState(queue.enqueue(i), capacity, takers, offerers) -> Sync[F].unit

          case BoundedState(queue, capacity, takers, offerers) =>
            BoundedState(queue, capacity, takers, offerers.enqueue(i -> offerer)) -> offerer.get
        }.flatten
      }
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
      stateR <- Ref.of[IO, BoundedState[IO, Int]](BoundedState.empty[IO, Int](capacity = 100))
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