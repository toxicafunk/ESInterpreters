package common

object models {
  type UUID = String

  type Error = String

  sealed trait BaseEntity {
    val id: UUID
  }

  sealed trait Input extends BaseEntity
  sealed trait Output extends BaseEntity

  sealed trait PaymentMethod extends Input {
    val platformId: Option[String]
  }
  final case class Credit(id: UUID, card: String, owner: String, platformId: Option[String]) extends PaymentMethod
  final case class PayPal(id: UUID, user: String, payPalToken: String, platformId: Option[String]) extends PaymentMethod

  case class ProvidedSection(section: String, hasLogisticMargin: Boolean)
  case class Provider(id: UUID, sections: List[ProvidedSection]) extends Input
  case class Store(id: UUID, tightFlowIndicator: String, logistic: Option[String]) extends Input
  case class ReplayMsg(id: UUID, offset: Long, event: String) extends Input
  case class Address(id: UUID, street: String, number: Int) extends Input
  case class SubProduct(id: UUID, platformId: Option[String]) extends Input
  case class Product(id: UUID, categoryId: UUID, ean: Option[String], providerId: UUID, subProducts: Map[UUID, SubProduct]) extends Input
  case class JsonOrder(id: UUID, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends Input

  case class CommerceItem(id: UUID, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean) extends Output
  case class PaymentGroup(id: UUID, logisticCircuit: String, address: Option[Address], paymentMethod: Option[PaymentMethod]) extends Output
  case class Order(id: UUID, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends Output

  case class Message[A <: BaseEntity](key: String, entity: A, command: String, timestamp: Long)
}