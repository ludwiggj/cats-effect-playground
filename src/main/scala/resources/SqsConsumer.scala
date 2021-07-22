package resources

trait SqsConsumer {
  def poll(): Unit = println("Polling SQS")

  def use(): Unit = poll()

  def close(): Unit = println("Closing SQS")
}

object SqsConsumer {
  def withSqsConsumer[T](resource: SqsConsumer)(handle: SqsConsumer => T): T = {
    try {
      handle(resource)
    } finally {
      resource.close()
    }
  }

  def apply(): SqsConsumer = {
    new SqsConsumer { println("Opening SQS") }
  }
}
