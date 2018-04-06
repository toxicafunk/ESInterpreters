package events

import common.models._

trait Event[A] {
  def at: Long
}

trait HapromEvent[A <: BaseEntity] extends Event[A]

case class HapromProductUpdated(id: Id, product: Product, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean, at: Long)
  extends HapromEvent[HapromProduct]

case class HapromSaleUpdated(id: Id, subProduct: SubProduct, logisticCircuit: Option[String], at: Long)
  extends HapromEvent[HapromSale]

case class HapromFailed[A <: BaseEntity](id: Id, entity: A, message: String, at: Long) extends HapromEvent[A]