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

import scala.collection.mutable.Queue
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

      case ReceiveMessage(brokers, topic, consumerGroup, autoCommit) => {
        consumerOpt = consumerOpt.orElse {
          val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
          cons.run()
          Some(cons)
        }

        Future.successful(consumerOpt.map(c => c.atomicQueue.getAndSet(Queue.empty).toStream.map(_.value())).getOrElse(Stream.Empty))
      }
    }
  }

  val futureOrdersInterpreter = new (OrdersAlgebra ~> Future) {

    def currentTime: Long = Instant.now().toEpochMilli

    type OrderOrderEvent = OrderEvent[Order, Order]
    type StreamOrderOrder = Stream[OrderOrderEvent]

    type CommerceItemEvent = OrderEvent[CommerceItem, Product]
    type StreamCommerceItem = Stream[CommerceItemEvent]

    type PaymentGroupEvent = OrderEvent[PaymentGroup, PaymentMethod]
    type StreamPaymentGroup = Stream[PaymentGroupEvent]

    type PaymentGroupAddressEvent = OrderEvent[PaymentGroup, Address]
    type StreamPaymentGroupAddress = Stream[PaymentGroupAddressEvent]

    override def apply[A](fa: OrdersAlgebra[A]): Future[A] = fa match {

      case CreateOrder(id, order) => {
        val event = OrderCreated(id, Order(id, List.empty, None).some, currentTime)

        eventLog.put(id, event) match {
          /*_*/
          case Left(err) => Future.successful(Stream(OrderUpdateFailed(id, order.some, order, err, currentTime)))
          case Right(evt) => Future.successful(Stream(evt.asInstanceOf[OrderOrderEvent]))
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
        }.toStream)
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
          case Left(err) => Future.successful(Stream(OrderUpdateFailed[PaymentGroup, PaymentMethod](orderId, None, paymentMethod, err, currentTime)))
          case Right(evt) => Future.successful(Stream(evt.asInstanceOf[PaymentGroupEvent]))
          /*_*/
        }

      }

      case AddPaymentAdress(orderId, address) => {
        val paymentGroup = PaymentGroup(orderId, "", address.some, None)
        val event = OrderPaymentAddressUpdated(orderId, paymentGroup.some, currentTime)

        eventLog.put(event.id, event) match {
          /*_*/
          case Left(err) => Future.successful(Stream(OrderUpdateFailed(orderId, None, address, err, currentTime)))
          case Right(evt) => Future.successful(Stream(evt.asInstanceOf[PaymentGroupAddressEvent]))
          /*_*/
        }

      }

      case Replay(_, replayMsg) => {
        println(s"Consumer is present ${consumerOpt.isDefined} - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
        consumerOpt.map(c => {
          println(s"Seeking offset ${replayMsg.offset}")
          c.replay(replayMsg.offset)
        })
        Future.successful(Stream.empty)
      }

      case UnknownCommand(id) => {
        println(s"Unknown command for $id");
        /*_*/
        Future.successful(Stream.Empty)
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
        val result: Future[Stream[String]] =
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
