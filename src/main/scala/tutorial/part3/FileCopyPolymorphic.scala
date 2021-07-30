package tutorial.part3

import cats.effect._
import cats.implicits._
import cats.effect.std.Console
import java.io._

object FileCopyPolymorphic extends IOApp {
  private def inputStream[F[_] : Sync](f: File): Resource[F, FileInputStream] =
    Resource.make {
      Sync[F].blocking(new FileInputStream(f)) // build
    } { inStream =>
      Sync[F].blocking(inStream.close()).handleErrorWith(_ => Sync[F].unit) // release
    }

  private def outputStream[F[_] : Sync](f: File): Resource[F, FileOutputStream] =
    Resource.make {
      Sync[F].blocking(new FileOutputStream(f)) // build
    } { outStream =>
      Sync[F].blocking(outStream.close()).handleErrorWith(_ => Sync[F].unit) // release
    }

  private def inputOutputStreams[F[_] : Sync](in: File, out: File): Resource[F, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

  // Sync is a typeclass that encodes the notion of suspending synchronous side effects in the F[_] context
  private def transmit[F[_] : Sync](origin: InputStream,
                                    destination: OutputStream,
                                    buffer: Array[Byte],
                                    bufferLength: Int,
                                    acc: Long): F[Long] = {
    for {
      amount <- Sync[F].blocking(origin.read(buffer, 0, bufferLength))
      count <-
        if (amount > -1)
          Sync[F].blocking(destination.write(buffer, 0, amount)) >>
            transmit(origin, destination, buffer, bufferLength, acc + amount)
        else Sync[F].pure(acc) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count
  } // Returns the actual amount of bytes transmitted

  // transfer will do the real work
  private def transfer[F[_] : Sync](origin: InputStream, destination: OutputStream): F[Long] = {
    val bufferLength = 10
    transmit(origin, destination, new Array[Byte](bufferLength), bufferLength, 0)
  }

  private def copy[F[_] : Sync](src: File, dest: File): F[Either[Throwable, Long]] = {
    inputOutputStreams(src, dest).use {
      case (s, d) => transfer(s, d)
    }.attempt
  }

  private def confirmFileOverwrite[F[_]: Sync: Console](filename: String): F[Boolean] =
    for {
      _ <- Console[F].println(s"This operation will overwrite file $filename. Is this OK (y/yes)?")
      resp <- Console[F].readLine
    } yield {
      resp.toLowerCase() match {
        case "y" | "yes" => true
        case _ => false
      }
    }

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      _ <-
        if (args.length < 2)
          IO.raiseError(new IllegalArgumentException("Need origin and destination files"))
        else
          IO.unit
      srcFilename = args.head
      destFilename = args(1)
      _ <-
        if (srcFilename == destFilename)
          IO.raiseError(new IllegalArgumentException(s"Origin and destination files have same value [$srcFilename]"))
        else
          IO.unit
      src = new File(srcFilename)
      dest = new File(destFilename)
      proceed <-
        if (dest.exists())
          confirmFileOverwrite[IO](destFilename)
        else
          IO.pure(true)
      _ <-
        if (!proceed)
          IO.raiseError(new IllegalArgumentException(
            s"Destination file [$destFilename] already exists, and you have declined to overwrite it."
          ))
        else
          IO.pure(())
      count <- FileCopyPolymorphic.copy[IO](src, dest)
      _ <-
        count match {
          case Left(t) => IO.raiseError(t)
          case _ => IO.unit
        }
      _ <- IO.println(s"$count bytes copied from ${src.getPath} to ${dest.getPath}")
    } yield ExitCode.Success).handleErrorWith(f =>
      IO.println(s"Error [${f.getMessage}]") *>
        IO.pure(ExitCode.Error)
    )
  }
}
