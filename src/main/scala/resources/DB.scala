package resources

trait DB {
  def use(): Unit = println("Querying DB")

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

  def apply(): DB = {
    new DB { println("Opening DB") }
  }
}