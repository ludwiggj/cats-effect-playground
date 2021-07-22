package resources

trait MyResource[R] { self =>
  def use[U](f: R => U): U

  def flatMap[B](mapping: R => MyResource[B]): MyResource[B] = {
    new MyResource[B] {
      override def use[U](f: B => U): U = self.use(mapping(_).use(f))
    }
  }

  def map[B](mapping: R => B): MyResource[B] = {
    new MyResource[B] {
      override def use[U](f: B => U): U = self.use(r => f(mapping(r)))
    }
  }
}

object MyResource {
  def make[R] (acquire: => R)(close: R => Unit): MyResource[R] = new MyResource[R] {
    override def use[U](f: R => U): U = {
      val resource = acquire
      try {
        f(resource)
      } finally {
        close(resource)
      }
    }
  }

  def pure[R](r: => R): MyResource[R] = make(r)(_ => ())
}