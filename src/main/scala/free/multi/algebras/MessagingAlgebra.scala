package free.multi.algebras

import cats.InjectK
import cats.free.Free
import common.models.ReplayMsg
import free.multi.Id


sealed trait MessagingAlgebra[T]

case class ReceiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean) extends MessagingAlgebra[Option[String]]

case class Commit() extends MessagingAlgebra[Unit]

case class SendMessage(brokers: String, topic: String, message: String) extends MessagingAlgebra[String]

case class Replay(id: Id, entity: ReplayMsg) extends MessagingAlgebra[Unit]

class Messages[F[_]](implicit I: InjectK[MessagingAlgebra, F]) {
  def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Free[F, Option[String]] =
    Free.inject[MessagingAlgebra, F](ReceiveMessage(brokers, topic, consumerGroup, autoCommit))

  def commit(): Free[F, Unit] = {
    Free.inject[MessagingAlgebra, F](Commit())
  }

  def sendMessage(brokers: String, topic: String, message: String): Free[F, String] = {
    Free.inject[MessagingAlgebra, F](SendMessage(brokers, topic, message))
  }

  def replay(id: Id, entity: ReplayMsg): Free[F, Unit] =
    Free.inject[MessagingAlgebra, F](Replay(id, entity))
}

object Messages {
  implicit def messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]): Messages[F] =
    new Messages[F]
}

