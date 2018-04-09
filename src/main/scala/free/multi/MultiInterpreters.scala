package free.multi

import java.time.Instant

import cats.implicits._
import cats.~>

import common.RestClient
import common.models._
import events._

import free.multi.Algebras._
import kafka.{Consumer, Producer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MultiInterpreters extends App {

  val futureMessagingInterpreter = new (MessagingAlgebra ~> Future) {

    var consumerOpt: Option[Consumer] = None

    override def apply[A](fa: MessagingAlgebra[A]): Future[A] = fa match {
      case ReceiveMessage(brokers, topic, consumerGroup, autoCommit) => {
        consumerOpt = consumerOpt.orElse {
          val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
          cons.run()
          Some(cons)
        }

        consumerOpt match {
          case None => Future(Stream.empty)
          case Some(c) => Future(c.atomicQueue.get().toStream.map(_.value()))
        }
      }

      case Commit() => Future.successful(consumerOpt.get.consumer.commitSync())

      case SendMessage(brokers, topic, message) => {
        val producer = new Producer(brokers)
        Future.successful(producer.sendMessage(topic, "", message))
      }
    }
  }

  val futureOrdersInterpreter = new (OrdersAlgebra ~> Future) {

    val eventLog = InMemoryEventStore.apply[String]

    override def apply[A](fa: OrdersAlgebra[A]): Future[A] = fa match {
      case AddCommerceItem(id, product, offset) => {
        val provider = RestClient.callProvider(product.providerId).unsafeRunSync()
        val section = provider.sections
           .filter(section => section.section == product.categoryId)
           .head

        val updatedEvents: Iterable[Either[Error, Event[_]]] = product.subProducts.map(entry => {
          val store = entry._2.platformId.map(p => {println(p); RestClient.callStore(p).unsafeRunSync()})
          val id = entry._1 + product.ean.getOrElse("")
          val commerceItem = CommerceItem(id, store.map(_.tightFlowIndicator), section.hasLogisticMargin)
          OrderCommerceItemUpdated(id, commerceItem, Instant.now().toEpochMilli)
        })
          .map(hpu => eventLog.put(hpu.id, hpu))

        Future.successful(updatedEvents.map {_ match {
          case Left(err) => OrderUpdateFailed(product.id, product, err, Instant.now().toEpochMilli)
          case Right(event) => event.asInstanceOf[OrderEvent[Product]]
        }}.toStream)
      }

      case AddPaymentAddress(id, subproduct, offset) => {
        val store = subproduct.platformId.map(RestClient.callStore(_).unsafeRunSync())
        val hsu = OrderPaymentGroupUpdated(id, subproduct, store.flatMap(_.logistic), Instant.now().toEpochMilli)
        val updatedSales = eventLog.put(hsu.id, hsu)
        Future.successful {
          updatedSales match {
            case Left(err) => OrderUpdateFailed(subproduct.id, subproduct, err, Instant.now().toEpochMilli)
            case Right(event) => event.asInstanceOf[OrderEvent[SubProduct]]
          }
        }
      }

      case Replay(id, offset, event) => ???
    }
  }

  val futureMessagingOrReportInterpreter = futureMessagingInterpreter or futureOrdersInterpreter

  import Programs._

  while (true) {
    val result: Future[Stream[String]] =
      processMessage("192.168.99.100:9092", "test", "testers", false)
        .foldMap(futureMessagingOrReportInterpreter)

    result.filter(!_.isEmpty).foreach(s => println(s"message processed: $s"))
    Thread.sleep(2000L)
  }
}
