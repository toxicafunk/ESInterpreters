package free.multi

import cats.InjectK
import cats.data.EitherK
import cats.free.Free
import common.models._
import events.{HapromEvent, HapromProductUpdated, HapromSaleUpdated}

object Algebras {
  type Message = String
  type Id = String

  sealed trait MessagingAlgebra[T]
  case class ReceiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean) extends MessagingAlgebra[Stream[Message]]
  case class Commit() extends MessagingAlgebra[Unit]
  case class SendMessage(brokers: String, topic: String, message: Message) extends MessagingAlgebra[Unit]

  class Messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]) {
    def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Free[F, Stream[Message]] =
      Free.inject(ReceiveMessage(brokers, topic, consumerGroup, autoCommit))

    def commit(): Free[F, Unit] = Free.inject(Commit())

    def sendMessage(brokers: String, topic: String, message: Message): Free[F, Unit] =
      Free.inject(SendMessage(brokers, topic, message))
  }
  object Messages {
    implicit def messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]): Messages[F] =
      new Messages
  }

  sealed trait HapromReportAlgebra[+T]
  case class UpdateHapromProduct(id: Id, entity: Product, offset: Int) extends HapromReportAlgebra[Stream[HapromEvent[Product]]]
  case class UpdateHapromSale(id: Id, entity: SubProduct, offset: Int) extends HapromReportAlgebra[HapromEvent[SubProduct]]
  case class Replay(id: Id, offset: Int, event: String) extends HapromReportAlgebra[Unit]

  class Reports[F[_]](implicit i: InjectK[HapromReportAlgebra, F]) {
    def updateHapromProduct(id: Id, entity: Product, offset: Int): Free[F, Stream[HapromEvent[Product]]] =
      Free.inject(UpdateHapromProduct(id, entity, offset))

    def updateHapromSale(id: Id, entity: SubProduct, offset: Int): Free[F, HapromEvent[SubProduct]] =
      Free.inject(UpdateHapromSale(id, entity, offset))

    def replay(id: Id, offset: Int, event: String): Free[F, Unit] =
      Free.inject(Replay(id, offset, event))
  }
  object Reports {
    implicit def reports[F[_]](implicit i: InjectK[HapromReportAlgebra, F]): Reports[F] =
      new Reports
  }

  type MessagingAndReportAlg[T] = EitherK[MessagingAlgebra, HapromReportAlgebra, T]

}

