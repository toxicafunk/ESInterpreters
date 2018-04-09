package events

import common.models._

trait Event[A] {
  val at: Long
}

trait OrderEvent[A <: BaseEntity] extends Event[A]

case class OrderCommerceItemUpdated(id: Id, commerceItem: CommerceItem, at: Long)
  extends OrderEvent[CommerceItem]

case class OrderPaymentGroupUpdated(id: Id, payment: PaymentGroup, at: Long)
  extends OrderEvent[PaymentGroup]

case class OrderUpdateFailed[A <: BaseEntity](id: Id, entity: A, message: String, at: Long) extends OrderEvent[A]
