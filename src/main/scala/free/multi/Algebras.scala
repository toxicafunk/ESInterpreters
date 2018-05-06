package free.multi

import cats.InjectK
import cats.data.EitherK
import cats.free.Free
import common.models._
import events.OrderEvent

object Algebras {
  //type Message = String
  type Id = String

  sealed trait MessagingAlgebra[T]
  case class ReceiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean) extends MessagingAlgebra[Stream[String]]
  case class Commit() extends MessagingAlgebra[Unit]
  case class SendMessage(brokers: String, topic: String, message: String) extends MessagingAlgebra[Unit]

  class Messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]) {
    def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Free[F, Stream[String]] =
      Free.inject(ReceiveMessage(brokers, topic, consumerGroup, autoCommit))

    def commit(): Free[F, Unit] = Free.inject(Commit())

    def sendMessage(brokers: String, topic: String, message: String): Free[F, Unit] =
      Free.inject(SendMessage(brokers, topic, message))
  }
  object Messages {
    implicit def messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]): Messages[F] =
      new Messages
  }

  sealed trait OrdersAlgebra[+T]
  case class CreateOrder(id: Id, entity: Order) extends OrdersAlgebra[Stream[OrderEvent[Order, Order]]]
  case class AddCommerceItem(orderId: Id, entity: Product, qty: Int) extends OrdersAlgebra[Stream[OrderEvent[CommerceItem, Product]]]
  case class AddPaymentMethod(orderId: Id, entity: PaymentMethod) extends OrdersAlgebra[Stream[OrderEvent[PaymentGroup, PaymentMethod]]]
  case class AddPaymentAdress(orderId: Id, entity: Address) extends OrdersAlgebra[Stream[OrderEvent[PaymentGroup, Address]]]
  case class UnknownCommand(orderId: Id) extends OrdersAlgebra[Stream[OrderEvent[Order, Order]]]
  case class Replay(id: Id, offset: Long, event: String) extends OrdersAlgebra[Unit]

  class Orders[F[_]](implicit i: InjectK[OrdersAlgebra, F]) {
    def createOrder(id: Id, entity: Order): Free[F, Stream[OrderEvent[Order, Order]]] =
      Free.inject(CreateOrder(id, entity))

    def addCommerceItem(orderId: Id, entity: Product, qty: Int): Free[F, Stream[OrderEvent[CommerceItem, Product]]] =
      Free.inject(AddCommerceItem(orderId, entity, qty))

    def addPaymentGroup(orderId: Id, entity: PaymentMethod): Free[F, Stream[OrderEvent[PaymentGroup, PaymentMethod]]] =
      Free.inject(AddPaymentMethod(orderId, entity))

    def addPaymentAddress(orderId: Id, entity: Address): Free[F, Stream[OrderEvent[PaymentGroup, Address]]] =
      Free.inject(AddPaymentAdress(orderId, entity))

    def unknownCommand(id: Id): Free[F, Stream[OrderEvent[Order, Order]]] =
      Free.inject(UnknownCommand(id))

    def replay(id: Id, offset: Long, event: String): Free[F, Unit] =
      Free.inject(Replay(id, offset, event))
  }
  object Orders {
    implicit def reports[F[_]](implicit i: InjectK[OrdersAlgebra, F]): Orders[F] =
      new Orders
  }

  type MessagingAndOrdersAlg[T] = EitherK[MessagingAlgebra, OrdersAlgebra, T]

}

