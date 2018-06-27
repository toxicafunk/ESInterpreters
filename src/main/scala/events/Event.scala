package events

import cats.implicits._
import common.models._

sealed trait Event[+A] {
  val at: Long
}

sealed trait OrderEvent[+O <: Output] extends Event[O] {
  val id: Id
  val entity: Option[O]

  def projection(implicit eventLog: EventStore[String]): Order = {
    eventLog.get(id).filter(evt => evt.at <= this.at).foldRight(Order("", List.empty, None))((evt, order) => evt.asInstanceOf[OrderEvent[_]].entity match {
      case None => order
      case Some(enty) => enty match {
        case o@Order(_, _, _) => o
        case c@CommerceItem(_, _, _) => order.copy(commerceItems = c +: order.commerceItems)
          // TODO: Use lenses
        case p@PaymentGroup(_, _, address, None) => order.paymentGroup match {
          case None => order.copy(paymentGroup = p.some)
          case Some(pGroup) => order.copy(paymentGroup = pGroup.copy(address = address).some)
        }
        case p@PaymentGroup(_, _, None, paymentMethod) => order.paymentGroup match {
          case None => order.copy(paymentGroup = p.some)
          case Some(pGroup) => order.copy(paymentGroup = pGroup.copy(paymentMethod = paymentMethod).some)
        }
      }
    })
  }
}

case class OrderCreated(id: Id, entity: Option[Order], at: Long)
  extends OrderEvent[Order]

case class OrderCommerceItemUpdated(id: Id, entity: Option[CommerceItem], at: Long)
  extends OrderEvent[CommerceItem]

case class OrderPaymentGroupUpdated(id: Id, entity: Option[PaymentGroup], at: Long)
  extends OrderEvent[PaymentGroup]

case class OrderPaymentAddressUpdated(id: Id, entity: Option[PaymentGroup], at: Long)
  extends OrderEvent[PaymentGroup]

case class OrderUpdateFailed[I <: Input, O <: Output](id: Id, entity: Option[O], baseEntity: I, message: String, at: Long) extends OrderEvent[O]
