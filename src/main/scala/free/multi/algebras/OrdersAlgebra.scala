package free.multi.algebras

import common.models._
import events.OrderEvent
import free.multi.Id

trait OrdersAlgebra[F[_]] {
  def createOrder(id: Id, entity: JsonOrder): F[OrderEvent[Order]]
  def addCommerceItem(orderId: Id, entity: SubProduct, product: Product, qty: Int): F[OrderEvent[CommerceItem]]
  def addPaymentMethod(orderId: Id, entity: PaymentMethod): F[OrderEvent[PaymentGroup]]
  def addPaymentAddress(orderId: Id, entity: Address): F[OrderEvent[PaymentGroup]]
  def unknownCommand(id: Id): F[OrderEvent[Order]]
}
