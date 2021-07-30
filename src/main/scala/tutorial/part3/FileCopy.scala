package tutorial.part3

import cats.effect.{ExitCode, IO, IOApp, Resource}

import java.io._
import scala.concurrent.duration.DurationInt

object FileCopy extends IOApp {
  private def inputStream(f: File): Resource[IO, FileInputStream] =
    Resource.make {
      IO.blocking(new FileInputStream(f)) // build
    } { inStream =>
      IO.println("Releasing input stream") >>
        IO.blocking(inStream.close()).handleErrorWith(_ => IO.unit) // release
    }

  private def outputStream(f: File): Resource[IO, FileOutputStream] =
    Resource.make {
      IO.blocking(new FileOutputStream(f)) // build
    } { outStream =>
      IO.println("Releasing output stream") >>
        IO.blocking(outStream.close()).handleErrorWith(_ => IO.unit) // release
    }

  private def inputOutputStreams(in: File, out: File): Resource[IO, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

  private def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO.blocking(origin.read(buffer, 0, buffer.length))
      count <-
        if (amount > -1)
        // TODO - Hmm, can't seem to interrupt the code with the sleep placed here...
        // Thankfully, Resource makes dealing with cancelation an easy task. If the IO inside a
        // Resource.use is canceled, the release section of that resource is run. In our example
        // this means the input and output streams will be properly closed. Also, cats-effect
        // does not cancel code inside IO.blocking instances. In the case of our transmit function
        // this means the execution would be interrupted only between two calls to IO.blocking. If
        // we want the execution of an IO instance to be interrupted when canceled, without waiting
        // for it to finish, we must instantiate it using IO.interruptible.

        // See https://typelevel.org/cats-effect/docs/2.x/datatypes/io#building-cancelable-io-tasks for IO cancellable
          IO.blocking(destination.write(buffer, 0, amount)) >>
            IO.sleep(2.seconds) >>
            transmit(origin, destination, buffer, acc + amount)
        else
          IO.pure(acc) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count // Returns the actual amount of bytes transmitted

  // transfer will do the real work
  private def transfer(origin: InputStream, destination: OutputStream): IO[Long] =
    transmit(origin, destination, new Array[Byte](10), 0)

  private def copy(origin: File, destination: File): IO[Long] = {
    inputOutputStreams(origin, destination).use {
      case (o, d) => transfer(o, d)
    }
  }

  // main loop
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException("Need origin and destination files"))
      else IO.unit
      orig = new File(args.head)
      dest = new File(args(1))
      count <- FileCopy.copy(orig, dest)
      _ <- IO.println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}")
    } yield ExitCode.Success
  }
}
