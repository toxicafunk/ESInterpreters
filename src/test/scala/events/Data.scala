package events

import common.models
import common.models.UUID
import events.Data.q
import free.multi.algebras._

import scala.collection.mutable.Queue

object Data {
  val createOrderMsg =  """{"key": "O123", "command": "createOrder", "timestamp": 1523524766634, "entity": {"id":"O123", "commerceItems": []}}"""
  val addCommerceItemMsg = """{"key": "O123", "command": "addCommerceItem", "timestamp": 1523524767634, "entity": {"id":"P123","categoryId":"01","ean": "890","providerId":"PR100","subProducts":{ "SP000": {"id":"SP000", "platformId": "abc01" },"SP001": {"id":"SP001", "platformId": "abc02" }}}}"""
  val addPaymentGroupMsg = """{"key": "O123", "command": "addPaymentGroup", "timestamp": 1523524769634, "entity": {"id":"PG123","card":"visa","owner":"Eric Rodriguez","platformId":"99"}}"""
  val addPaymentAddressMsg = """{"key": "O123", "command": "addPaymentAddress", "timestamp": 1523524773634, "entity": {"id":"A123","street":"Calle Buena VIsta", "number": 11}}"""

  val q: Queue[String] = Queue()
  val messages = List(createOrderMsg, addCommerceItemMsg, addPaymentGroupMsg, addPaymentAddressMsg)

  implicit val eventLog: EventStore[String] = InMemoryEventStore.apply[String]

}

import scala.concurrent.Future

trait TestingInterpreter extends MessagingAlgebra[Future] {

  override def commit(): Future[Unit] = Future.unit

  override def sendMessage(brokers: String, topic: String, message: String): Future[String] = {
    println(s"Publishing: $message")
    Future.successful(message)
  }

  override def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Future[Option[String]] = {
    val msg = Some(q.dequeue())
    println(s"Received: $msg")
    Future.successful(msg)
  }

  override def replay(id: UUID, entity: models.ReplayMsg): Future[String] = Future.successful("Replayed!")
}
