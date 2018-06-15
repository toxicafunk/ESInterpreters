package events

import cats.~>
import free.multi.Algebras._
import free.multi.MultiInterpreters.futureOrdersInterpreter

import scala.collection.mutable.Queue
import scala.concurrent.Future

object Data {
  val createOrderMsg =  """{"key": "O123", "command": "createOrder", "timestamp": 1523524766634, "entity": {"id":"O123", "commerceItems": []}}"""
  val addCommerceItemMsg = """{"key": "O123", "command": "addCommerceItem", "timestamp": 1523524767634, "entity": {"id":"P123","categoryId":"01","ean": "890","providerId":"PR100","subProducts":{ "SP000": {"id":"SP000", "platformId": "abc01" }}}}"""
  val addPaymentGroupMsg = """{"key": "O123", "command": "addPaymentGroup", "timestamp": 1523524769634, "entity": {"id":"PG123","card":"visa","owner":"Eric Rodriguez","platformId":"99"}}"""
  val addPaymentAddressMsg = """{"key": "O123", "command": "addPaymentAddress", "timestamp": 1523524773634, "entity": {"id":"A123","street":"Calle Buena VIsta", "number": 11}}"""

  val q: Queue[String] = Queue()
  val messages = List(createOrderMsg, addCommerceItemMsg, addPaymentGroupMsg, addPaymentAddressMsg)

  val futureTestingInterpreter = new (MessagingAlgebra ~> Future) {

    override def apply[A](fa: MessagingAlgebra[A]): Future[A] = fa match {

      case Commit() => /*_*/ Future.unit /*_*/

      case SendMessage(brokers@_, topic@_, message@_) =>
        /*_*/
        Future.successful(message)
        /*_*/

      case ReceiveMessage(brokers@_, topic@_, consumerGroup@_, autoCommit@_) =>
        /*_*/
        val msg = Some(q.dequeue())
        println(s"Received: $msg")
        Future.successful(msg)
      /*_*/
    }
  }

  val futureTestingOrReportInterpreter = futureTestingInterpreter or futureOrdersInterpreter

}
