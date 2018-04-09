package common

object models {
  type Id = String

  type Error = String

  sealed trait PaymentMethod
  final case class Credit(card: String, owner: String) extends PaymentMethod
  final case class PayPal(user: String, payPalToken: String) extends PaymentMethod

  sealed trait BaseEntity {
    val id: Id
  }

  case class ProvidedSection(section: String, hasLogisticMargin: Boolean)
  case class Provider(id: Id, sections: List[ProvidedSection]) extends BaseEntity
  case class Store(id: Id, tightFlowIndicator: String, logistic: Option[String]) extends BaseEntity

  case class SubProduct(id: Id, platformId: Option[String]) extends BaseEntity
  case class Product(id: Id, categoryId: Id, ean: Option[String], providerId: Id, subProducts: Map[Id, SubProduct]) extends BaseEntity
  // {"id":"P123","categoryId":"01", "ean": "890", "providerId": "PR100", "subProducts": { "SP000": {"id":"SP000", "platformId": "abc01" }}}

  case class Address(id: Id, street: String, number: Int) extends BaseEntity

  case class CommerceItem(id: Id, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean) extends BaseEntity
  case class PaymentGroup(id: Id, logisticCircuit: String, address: Address, paymentMethod: PaymentMethod) extends BaseEntity

  case class Order(id: Id, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends BaseEntity

  case class Message[A <: BaseEntity](key: String, entity: Option[A], timestamp: Long)
}