package free

import cats.free.Free

object Algebras {
  type Message = String
  type Id = String

  sealed trait MessagingAlgebra[T]
  case class ReceiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean) extends MessagingAlgebra[Stream[Message]]
  case class Commit() extends MessagingAlgebra[Unit]
  case class SendMessage(brokers: String, topic: String, message: Message) extends MessagingAlgebra[Unit]

  type MessagingService[T] = Free[MessagingAlgebra, T]

  def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): MessagingService[Stream[Message]] =
    Free.liftF(ReceiveMessage(brokers, topic, consumerGroup, autoCommit))

  def commit(): MessagingService[Unit] = Free.liftF(Commit())

  def sendMessage(brokers: String, topic: String, message: Message): MessagingService[Unit] =
    Free.liftF(SendMessage(brokers, topic, message))
}

