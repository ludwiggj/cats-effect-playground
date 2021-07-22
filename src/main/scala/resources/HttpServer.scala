package resources

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

  def apply(): HttpServer = {
    new HttpServer { println("Opening Http Server") }
  }
}