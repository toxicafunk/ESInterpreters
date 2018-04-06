package common

object models {
  type Id = String

  type Error = String

  sealed trait BaseEntity {
    val id: Id
  }

  case class ProvidedSection(section: String, hasLogisticMargin: Boolean)
  case class Provider(id: Id, sections: List[ProvidedSection]) extends BaseEntity
  case class Store(id: Id, tightFlowIndicator: String, logistic: Option[String]) extends BaseEntity

  case class SubProduct(id: Id, platformId: Option[String]) extends BaseEntity
  case class Product(id: Id, categoryId: Id, ean: Option[String], providerId: Id, subProducts: Map[Id, SubProduct]) extends BaseEntity
  // {"id":"P123","categoryId":"01", "ean": "890", "providerId": "PR100", "subProducts": { "SP000": {"id":"SP000", "platformId": "abc01" }}}

  case class HapromProduct(id: Id, tightFlowIndicator: Option[String], hasLogisticMargin: Boolean) extends BaseEntity
  case class HapromSale(id: Id, logisticCircuit: String) extends BaseEntity
}