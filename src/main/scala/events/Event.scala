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
    eventLog.get(id).foldRight(Order("", List.empty, None))((evt, order) => evt.asInstanceOf[OrderEvent[_]].entity match {
      case None => order
      case Some(enty) => enty match {
        case o@Order(_, _, _) => o
        case c@CommerceItem(_, _, _) => order.copy(commerceItems = c +: order.commerceItems)
        case p@PaymentGroup(_, _, _, _) => order.copy(paymentGroup = p.some)
      }
    })
  }

  val toOutput = this.asInstanceOf[OrderEvent[Output]]
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
