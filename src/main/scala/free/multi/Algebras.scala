package free.multi

import cats.InjectK
import cats.data.EitherK
import cats.free.Free
import common.models._
import events._

object Algebras {
  type Id = String

  sealed trait MessagingAlgebra[T]
  case class ReceiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean) extends MessagingAlgebra[Option[String]]
  case class Commit() extends MessagingAlgebra[Unit]
  case class SendMessage(brokers: String, topic: String, message: String) extends MessagingAlgebra[String]

  class Messages[F[_]](implicit I: InjectK[MessagingAlgebra, F]) {
    def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Free[F, Option[String]] =
      Free.inject[MessagingAlgebra, F](ReceiveMessage(brokers, topic, consumerGroup, autoCommit))

    def commit(): Free[F, Unit] = {
      Free.inject[MessagingAlgebra, F](Commit())
    }

    def sendMessage(brokers: String, topic: String, message: String): Free[F, String] = {
      Free.inject[MessagingAlgebra, F](SendMessage(brokers, topic, message))
    }
  }
  object Messages {
    implicit def messages[F[_]](implicit i: InjectK[MessagingAlgebra, F]): Messages[F] =
      new Messages[F]
  }

  sealed trait OrdersAlgebra[+T]
  case class CreateOrder(id: Id, entity: JsonOrder) extends OrdersAlgebra[OrderEvent[Order]]
  case class AddCommerceItem(orderId: Id, entity: SubProduct, product: Product, qty: Int) extends OrdersAlgebra[OrderEvent[CommerceItem]]
  case class AddPaymentMethod(orderId: Id, entity: PaymentMethod) extends OrdersAlgebra[OrderEvent[PaymentGroup]]
  case class AddPaymentAdress(orderId: Id, entity: Address) extends OrdersAlgebra[OrderEvent[PaymentGroup]]
  case class UnknownCommand(orderId: Id) extends OrdersAlgebra[OrderEvent[Order]]
  case class Replay(id: Id, entity: ReplayMsg) extends OrdersAlgebra[Unit]

  class Orders[F[_]](implicit i: InjectK[OrdersAlgebra, F]) {
    def createOrder(id: Id, entity: JsonOrder): Free[F, OrderEvent[Order]] =
      Free.inject[OrdersAlgebra, F](CreateOrder(id, entity))

    def addCommerceItem(orderId: Id, entity: SubProduct, product: Product, qty: Int): Free[F, OrderEvent[CommerceItem]] =
      Free.inject[OrdersAlgebra, F](AddCommerceItem(orderId, entity, product, qty))

    def addPaymentMethod(orderId: Id, entity: PaymentMethod): Free[F, OrderEvent[PaymentGroup]] =
      Free.inject[OrdersAlgebra, F](AddPaymentMethod(orderId, entity))

    def addPaymentAddress(orderId: Id, entity: Address): Free[F, OrderEvent[PaymentGroup]] =
      Free.inject[OrdersAlgebra, F](AddPaymentAdress(orderId, entity))

    def unknownCommand(id: Id): Free[F, OrderEvent[Order]] =
      Free.inject[OrdersAlgebra, F](UnknownCommand(id))

    def replay(id: Id, entity: ReplayMsg): Free[F, Unit] =
      Free.inject[OrdersAlgebra, F](Replay(id, entity))
  }
  object Orders {
    implicit def reports[F[_]](implicit I: InjectK[OrdersAlgebra, F]): Orders[F] =
      new Orders[F]
  }

  type MessagingAndOrdersAlg[T] = EitherK[MessagingAlgebra, OrdersAlgebra, T]

  //type MessagingAndOrdersAlgOption[T] = EitherK[Option, MessagingAndOrdersAlg, T]

}

