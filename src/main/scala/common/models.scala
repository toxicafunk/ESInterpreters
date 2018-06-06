package common

object models {
  type Id = String

  type Error = String

  sealed trait BaseEntity {
    val id: Id
  }

  sealed trait Input extends BaseEntity
  sealed trait Output extends BaseEntity

  sealed trait PaymentMethod extends Input {
    val platformId: Option[String]
  }
  final case class Credit(id: Id, card: String, owner: String, platformId: Option[String]) extends PaymentMethod
  final case class PayPal(id: Id, user: String, payPalToken: String, platformId: Option[String]) extends PaymentMethod

  case class ProvidedSection(section: String, hasLogisticMargin: Boolean)
  case class Provider(id: Id, sections: List[ProvidedSection]) extends Input
  case class Store(id: Id, tightFlowIndicator: String, logistic: Option[String]) extends Input
  case class ReplayMsg(id: Id, offset: Long, event: String) extends Input
  case class Address(id: Id, street: String, number: Int) extends Input
  case class SubProduct(id: Id, platformId: Option[String]) extends Input
  case class Product(id: Id, categoryId: Id, ean: Option[String], providerId: Id, subProducts: Map[Id, SubProduct]) extends Input
  case class JsonOrder(id: Id, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends Input

  case class CommerceItem(id: Id, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean) extends Output
  case class PaymentGroup(id: Id, logisticCircuit: String, address: Option[Address], paymentMethod: Option[PaymentMethod]) extends Output
  case class Order(id: Id, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends Output

  case class Message[A <: BaseEntity](key: String, entity: A, command: String, timestamp: Long)
}