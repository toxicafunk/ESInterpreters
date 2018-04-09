package free.multi

import cats.InjectK
import cats.data.EitherK
import cats.free.Free
import common.models._
import events.{OrderEvent, OrderCommerceItemUpdated, OrderPaymentGroupUpdated}

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

  sealed trait OrdersAlgebra[+T]
  case class CreateOrder(id: Id) extends OrdersAlgebra[Stream[OrderEvent[Order]]]
  case class AddCommerceItem(id: Id, entity: Product, qty: Int) extends OrdersAlgebra[Stream[OrderEvent[Product]]]
  case class AddPaymentGroup(id: Id, entity: PaymentMethod) extends OrdersAlgebra[OrderEvent[PaymentMethod]]
  case class AddPaymentAdress(id: Id, entity: Address) extends OrdersAlgebra[OrderEvent[Address]]
  case class Replay(id: Id, offset: Int, event: String) extends OrdersAlgebra[Unit]

  class Orders[F[_]](implicit i: InjectK[OrdersAlgebra, F]) {
    def createOrder(id: Id): Free[F, Stream[OrderEvent[Order]]] =
      Free.inject(CreateOrder(id))

    def addCommerceItem(id: Id, entity: Product, qty: Int): Free[F, Stream[OrderEvent[Product]]] =
      Free.inject(AddCommerceItem(id, entity, qty))

    def addPaymentGroup(id: Id, entity: PaymentMethod): Free[F, OrderEvent[PaymentMethod]] =
      Free.inject(AddPaymentGroup(id, entity))

    def addPaymentAddress(id: Id, entity: Address): Free[F, OrderEvent[Address]] =
      Free.inject(AddPaymentAdress(id, entity))

    def replay(id: Id, offset: Int, event: String): Free[F, Unit] =
      Free.inject(Replay(id, offset, event))
  }
  object Orders {
    implicit def reports[F[_]](implicit i: InjectK[OrdersAlgebra, F]): Orders[F] =
      new Orders
  }

  type MessagingAndOrdersAlg[T] = EitherK[MessagingAlgebra, OrdersAlgebra, T]

}

