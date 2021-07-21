package resources

import resources.DB.withDB
import resources.HttpServer.withHttpServer
import resources.SqsConsumer.withSqsConsumer

trait SqsConsumer {
  def poll(): Unit = println("SqsConsumer polling")

  def close(): Unit = println("SqsConsumer closed")
}

object SqsConsumer {
  def withSqsConsumer[T](resource: SqsConsumer)(handle: SqsConsumer => T): T = {
    try {
      handle(resource)
    } finally {
      resource.close()
    }
  }
}

trait DB {
  def close(): Unit = println("Closing DB")
}

object DB {
  def withDB[T](resource: DB)(handle: DB => T): T = {
    try {
      handle(resource)
    } finally {
      resource.close()
    }
  }
}

trait HttpServer {
  def close(): Unit = println("Closing Http server")
}

object HttpServer {
  def withHttpServer[T](resource: HttpServer)(handle: HttpServer => T): T = {
    try {
      handle(resource)
    } finally {
      resource.close()
    }
  }
}

object ResourcesWorkout {
  private val sqsConsumer = new SqsConsumer {}
  private val dbResource: Resource[DB] = Resource.make(new DB { println("Opening DB") })(_.close())
  private val httpServerResource: Resource[HttpServer] = Resource.make(new HttpServer { println("Opening Http Server") })(_.close())

  def firstSqsLifecycle(): Unit = {
    println("firstSqsLifecycle...")
    try {
      // using sqsConsumer
      sqsConsumer.poll()
    } finally {
      sqsConsumer.close()
    }
  }

  def secondSqsLifecycle(): Unit = {
    println("secondSqsLifecycle...")
    withSqsConsumer(new SqsConsumer {})(_.poll())
  }

  def dbAndServer(): Unit = {
    println("dbAndServer...")
    withDB(new DB { println("Opening DB") }) { db =>
      withHttpServer(new HttpServer { println("Opening Http Server") }) { server =>
        println("Using DB and HttpServer")
      }
    }
  }

  def dbAndServerTake2(): Unit = {
    println("dbAndServerTake2...")
    dbResource.use { db =>
      httpServerResource.use { httpServer =>
        println("Using DB and HttpServer")
      }
    }
  }

  def dbAndServerTake3(): Unit = {
    println("dbAndServerTake3...")
    val r = dbResource.flatMap { db =>
      httpServerResource.flatMap { httpServer =>
        Resource.pure(())
      }
    }

    r.use(_ => println("Using DB and HttpServer"))
  }

  def dbAndServerForComp(): Unit = {
    println("dbAndServerForComp...")

    val resources: Resource[(HttpServer, DB)] = for {
      db <- dbResource
      httpServer <- httpServerResource
    } yield (httpServer, db)

    resources.use {
      case (httpServer, db) => println("Running program, that uses http server and db...")
    }
  }

  def main(args: Array[String]): Unit = {
    firstSqsLifecycle()
    println()
    secondSqsLifecycle()
    println()
    dbAndServer()
    println()
    dbAndServerTake2()
    println()
    dbAndServerTake3()
    println()
    dbAndServerForComp()
  }
}