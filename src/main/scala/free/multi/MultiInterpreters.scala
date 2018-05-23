package free.multi

import java.time.Instant
import java.util.concurrent.{ExecutorService, Executors}

import cats.implicits._
import cats.~>
import common.RestClient
import common.models._
import events._
import free.multi.Algebras._
import kafka.{Consumer, Producer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MultiInterpreters {

  var consumerOpt: Option[Consumer] = None
  var producerOpt: Option[Producer] = None

  val futureMessagingInterpreter = new (MessagingAlgebra ~> Future) {

    override def apply[A](fa: MessagingAlgebra[A]): Future[A] = fa match {

      case Commit() => {
        println("Committing")
        /*_*/
        Future.successful(consumerOpt.get.consumer.commitSync())
        /*_*/
      }

      case SendMessage(brokers, topic, message) => {
        println(s"SendMessage: $brokers - $topic - $message")
        producerOpt = producerOpt.orElse(Some(new Producer(brokers)))
        /*_*/
        producerOpt.map(p => p.sendMessage(topic, "", message)).map(Future.successful).getOrElse(Future.successful("Error sending message"))
        /*_*/
      }

      case ReceiveMessage(brokers, topic, consumerGroup, autoCommit) =>
        consumerOpt = consumerOpt.orElse {
          val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
          cons.run()
          Some(cons)
        }

        consumerOpt
          .map(c => {
            val q = c.atomicQueue.get()
            if (q.isEmpty) ""
            else q.dequeue().value()
          })
          .filter(s => !s.isEmpty)
          .map(Future.successful(_))
          .getOrElse(Future.never)

    }
  }

  val futureOrdersInterpreter = new (OrdersAlgebra ~> Future) {

    def currentTime: Long = Instant.now().toEpochMilli

    type OrderOrderEvent = OrderEvent[Order, Order]

    type CommerceItemEvent = OrderEvent[CommerceItem, Product]

    type PaymentGroupEvent = OrderEvent[PaymentGroup, PaymentMethod]

    type PaymentGroupAddressEvent = OrderEvent[PaymentGroup, Address]

    override def apply[A](fa: OrdersAlgebra[A]): Future[A] = fa match {

      case CreateOrder(id, order) => {
        val event = OrderCreated(id, Order(id, List.empty, None).some, currentTime)

        eventLog.put(id, event) match {
          /*_*/
          case Left(err) => Future.successful(OrderUpdateFailed(id, order.some, order, err, currentTime))
          case Right(evt) => Future.successful(evt.asInstanceOf[OrderOrderEvent])
          /*_*/
        }
      }

      case AddCommerceItem(id, product, qty@_) => {
        val provider = RestClient.callProvider(product.providerId).unsafeRunSync()
        val section = provider.sections
          .filter(section => section.section == product.categoryId)
          .head

        val updatedEvents: Iterable[Either[Error, Event[_]]] = product.subProducts.map(entry => {
          val store = entry._2.platformId.map(p => {
            println(p);
            RestClient.callStore(p).unsafeRunSync()
          })
          val ciId = entry._1 + product.ean.getOrElse("")
          val commerceItem = CommerceItem(ciId, store.map(_.tightFlowIndicator), section.hasLogisticMargin)
          OrderCommerceItemUpdated(id, commerceItem.some, currentTime)
        })
          .map(hpu => eventLog.put(hpu.id, hpu))

        /*_*/
        Future.successful(updatedEvents.map {
          case Left(err) => OrderUpdateFailed[CommerceItem, Product](id, None, product, err, currentTime)
          case Right(event) => event.asInstanceOf[CommerceItemEvent]
        }.toList)
        /*_*/
      }

      case AddPaymentMethod(orderId, paymentMethod) => {
        val store = paymentMethod.platformId.map(RestClient.callStore(_).unsafeRunSync())
        println(s"Store: $store")
        val method = paymentMethod match {
          case c@Credit(_, _, _, _) => c
          case p@PayPal(_, _, _, _) => p
        }
        val paymentGroup = PaymentGroup(orderId, store.flatMap(_.logistic).getOrElse(""), None, method.some)
        val event = OrderPaymentGroupUpdated(orderId, paymentGroup.some, currentTime)
        println(event)

        eventLog.put(event.id, event) match {
          /*_*/
          case Left(err) => Future.successful(OrderUpdateFailed[PaymentGroup, PaymentMethod](orderId, None, paymentMethod, err, currentTime))
          case Right(evt) => Future.successful(evt.asInstanceOf[PaymentGroupEvent])
          /*_*/
        }
      }

      case AddPaymentAdress(orderId, address) => {
        val paymentGroup = PaymentGroup(orderId, "", address.some, None)
        val event = OrderPaymentAddressUpdated(orderId, paymentGroup.some, currentTime)

        eventLog.put(event.id, event) match {
          /*_*/
          case Left(err) => Future.successful(OrderUpdateFailed(orderId, None, address, err, currentTime))
          case Right(evt) => Future.successful(evt.asInstanceOf[PaymentGroupAddressEvent])
          /*_*/
        }

      }

      case Replay(_, replayMsg) => {
        println(s"Consumer is present ${consumerOpt.isDefined} - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
        consumerOpt.map(c => {
          println(s"Seeking offset ${replayMsg.offset}")
          c.replay(replayMsg.offset)
        })
        Future.unit
      }

      case UnknownCommand(id) => {
        val msg = s"Unknown command for $id"
        println(msg);
        val order = Order(id, List.empty, None)
        /*_*/
        Future.successful(OrderUpdateFailed(id, None, order, msg, currentTime))
        /*_*/
      }
    }
  }

  val futureMessagingOrReportInterpreter = futureMessagingInterpreter or futureOrdersInterpreter

  import Programs._

  var executor: ExecutorService = Executors.newSingleThreadExecutor

  def run(): Unit = {
    executor.execute(() => {
      println(s"Interpreter executing on thread ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
      while (true) {
        val result: Future[String] =
          processMessage("192.168.99.100:9092", "test", "testers", false)
            .foldMap(futureMessagingOrReportInterpreter)

        result.filter(_.nonEmpty).foreach(s => println(s"message processed: $s"))
        Thread.sleep(2000L)
      }
    })
  }

  def shutdown(): Unit = {
    if (executor != null)
      executor.shutdown()
  }

}
