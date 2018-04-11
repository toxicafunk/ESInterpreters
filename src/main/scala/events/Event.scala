package events

import common.models._

trait Event[A] {
  val at: Long
}

trait OrderEvent[A <: BaseEntity, B <: BaseEntity] extends Event[A] {
  val id: Id
  val entity: Option[A]
}

case class OrderCreated(id: Id, entity: Option[Order], at: Long)
  extends OrderEvent[Order, Order]

case class OrderCommerceItemUpdated(id: Id, entity: Option[CommerceItem], at: Long)
  extends OrderEvent[CommerceItem,Product]

case class OrderPaymentGroupUpdated(id: Id, entity: Option[PaymentGroup], at: Long)
  extends OrderEvent[PaymentGroup, PaymentMethod]

case class OrderPaymentAddressUpdated(id: Id, entity: Option[PaymentGroup], at: Long)
  extends OrderEvent[PaymentGroup, Address]

case class OrderUpdateFailed[A <: BaseEntity, B <: BaseEntity](id: Id, entity: Option[A], baseEntity: B, message: String, at: Long) extends OrderEvent[A, B]
