package free.multi.algebras

import cats.InjectK
import cats.free.Free
import common.models._
import events.OrderEvent
import free.multi.Id

sealed trait OrdersAlgebra[+T]

case class CreateOrder(id: Id, entity: JsonOrder) extends OrdersAlgebra[OrderEvent[Order]]

case class AddCommerceItem(orderId: Id, entity: SubProduct, product: Product, qty: Int) extends OrdersAlgebra[OrderEvent[CommerceItem]]

case class AddPaymentMethod(orderId: Id, entity: PaymentMethod) extends OrdersAlgebra[OrderEvent[PaymentGroup]]

case class AddPaymentAdress(orderId: Id, entity: Address) extends OrdersAlgebra[OrderEvent[PaymentGroup]]

case class UnknownCommand(orderId: Id) extends OrdersAlgebra[OrderEvent[Order]]

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
}

object Orders {
  implicit def reports[F[_]](implicit I: InjectK[OrdersAlgebra, F]): Orders[F] =
    new Orders[F]
}
