package free

import cats.Applicative
import cats.implicits._

package object multi {
  type Id = String

  import scala.language.higherKinds

  implicit final class StreamOps[F[_]: Applicative, B](val stream: Stream[F[B]]) {

    def streamTraverse[A](stream: Stream[A])(f: A => F[B]): F[Stream[B]] =
      stream.foldLeft(Stream.empty[B].pure[F]) { (accum, item) =>
        (accum, f(item)).mapN(_ :+ _)
      }

    def streamSequence: F[Stream[B]] =
      streamTraverse(stream)(identity)
  }
}
