package common

import common.models.BaseEntity
import io.circe.Decoder

object models {
  type Id = String

  type Error = String

  sealed trait BaseEntity {
    val id: Id
  }

  sealed trait PaymentMethod extends BaseEntity {
    val platformId: Option[String]
  }
  final case class Credit(id: Id, card: String, owner: String, platformId: Option[String]) extends PaymentMethod
  final case class PayPal(id: Id, user: String, payPalToken: String, platformId: Option[String]) extends PaymentMethod

  case class ProvidedSection(section: String, hasLogisticMargin: Boolean)
  case class Provider(id: Id, sections: List[ProvidedSection]) extends BaseEntity
  case class Store(id: Id, tightFlowIndicator: String, logistic: Option[String]) extends BaseEntity

  case class SubProduct(id: Id, platformId: Option[String]) extends BaseEntity
  case class Product(id: Id, categoryId: Id, ean: Option[String], providerId: Id, subProducts: Map[Id, SubProduct]) extends BaseEntity
  // {"id":"P123","categoryId":"01", "ean": "890", "providerId": "PR100", "subProducts": { "SP000": {"id":"SP000", "platformId": "abc01" }}}

  case class Address(id: Id, street: String, number: Int) extends BaseEntity

  case class CommerceItem(id: Id, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean) extends BaseEntity
  case class PaymentGroup(id: Id, logisticCircuit: String, address: Option[Address], paymentMethod: PaymentMethod) extends BaseEntity

  case class Order(id: Id, commerceItems: List[CommerceItem], paymentGroup: Option[PaymentGroup]) extends BaseEntity

  case class Message[A <: BaseEntity](key: String, entity: A, command: String, timestamp: Long)

}