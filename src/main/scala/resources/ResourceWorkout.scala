package resources

import resources.DB.withDB
import resources.HttpServer.withHttpServer
import resources.SqsConsumer.withSqsConsumer

//noinspection ScalaUnusedSymbol
object ResourceWorkout {
  private val dbResource: MyResource[DB] = MyResource.make(DB())(_.close())
  private val httpServerResource: MyResource[HttpServer] = MyResource.make(HttpServer())(_.close())

  def firstSqsLifecycle(): Unit = {
    println("firstSqsLifecycle...")
    val sqsConsumer = SqsConsumer()
    try {
      // using sqsConsumer
      sqsConsumer.poll()
    } finally {
      sqsConsumer.close()
    }
  }

  def secondSqsLifecycle(): Unit = {
    println("secondSqsLifecycle...")
    withSqsConsumer(SqsConsumer())(_.poll())
  }

  def dbAndServer(): Unit = {
    println("dbAndServer...")
    withDB(DB()) { db =>
      withHttpServer(HttpServer()) { server =>
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
        MyResource.pure(())
      }
    }

    r.use(_ => println("Using DB and HttpServer"))
  }

  def dbAndServerForComp(): Unit = {
    println("dbAndServerForComp...")

    val resources: MyResource[(HttpServer, DB)] = for {
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