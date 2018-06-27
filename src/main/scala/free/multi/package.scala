package free

import cats.Applicative
import cats.implicits._

package object multi {
  type Id = String

  import scala.language.higherKinds

  def streamTraverse[F[_] : Applicative, A, B](stream: Stream[A])(func: A => F[B]): F[Stream[B]] =
    stream.foldLeft(Stream.empty[B].pure[F]) { (accum, item) =>
      (accum, func(item)).mapN(_ :+ _)
    }

  def streamSequence[F[_] : Applicative, B](stream: Stream[F[B]]): F[Stream[B]] =
    streamTraverse(stream)(identity)
}
