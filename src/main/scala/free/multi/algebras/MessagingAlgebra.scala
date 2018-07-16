package free.multi.algebras

import common.models.ReplayMsg
import free.multi.Id


trait MessagingAlgebra[F[_]] {
  def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): F[Option[String]]
  def commit(): F[Unit]
  def sendMessage(brokers: String, topic: String, message: String): F[String]
  def replay(id: Id, entity: ReplayMsg): F[String]
}
