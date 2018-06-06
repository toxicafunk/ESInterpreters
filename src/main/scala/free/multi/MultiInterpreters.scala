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

        /*_*/
        Future.successful {
          consumerOpt
            .map(c => {
              val q = c.atomicQueue.get()
              if (q.isEmpty) ""
              else q.dequeue().value()
            })
            .filter(s => !s.isEmpty)
        }
        /*_*/
    }
  }

  val futureOrdersInterpreter = new (OrdersAlgebra ~> Future) {

    def failedEvent[O <: Output]: String => OrderEvent[O] = (id:String) => OrderUpdateFailed[Input, O](id, None, JsonOrder(id, List.empty, None),"Interpreter failed!", Instant.now().toEpochMilli)

    // def failedEvent[O <: Output]: String => OrderEvent[O] = (id: Id, in: Input, msg: String) => OrderUpdateFailed[Input, O](id, None, in, msg, currentTime())

    val currentTime = () => Instant.now().toEpochMilli

    override def apply[A](fa: OrdersAlgebra[A]): Future[A] = fa match {

      case CreateOrder(id, jsonOrder) =>
        val event = OrderCreated(id, Order(jsonOrder.id, List.empty[CommerceItem], None).some, currentTime())

        /*_*/
        eventLog.put(id, event)
          .bimap(
            _ => failedEvent[Order](id),
            evt => evt.asInstanceOf[OrderCreated]
          )
          .fold(fail => Future.successful(fail), success => Future.successful(success))
          /*_*/

      case AddCommerceItem(id, subProduct, product, qty@_) => {
        val provider = RestClient.callProvider(product.providerId).unsafeRunSync()
        val section = provider.sections
          .filter(section => section.section == product.categoryId)
          .head

        val store = subProduct.platformId.map(p => {
          println(p);
          RestClient.callStore(p).unsafeRunSync()
        })
        val ciId = subProduct.id + product.ean.getOrElse("")
        val commerceItem = CommerceItem(ciId, store.map(_.tightFlowIndicator), section.hasLogisticMargin)
        val ocu = OrderCommerceItemUpdated(id, commerceItem.some, currentTime())
        /*_*/
        eventLog.put(ocu.id, ocu)
            .bimap(
              _ => failedEvent[CommerceItem](id),
              evt => evt.asInstanceOf[OrderCommerceItemUpdated]
            )

          .fold(fail => Future.successful(fail), success => Future.successful(success))
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
        val event = OrderPaymentGroupUpdated(orderId, paymentGroup.some, currentTime())
        println(event)

        eventLog.put(event.id, event)
          .bimap(
            _ => failedEvent[PaymentGroup](orderId),
            evt => evt.asInstanceOf[OrderPaymentGroupUpdated]
          )
          /*_*/
          .fold(fail => Future.successful(fail), success => Future.successful(success))
          /*_*/
      }

      case AddPaymentAdress(orderId, address) => {
        val paymentGroup = PaymentGroup(orderId, "", address.some, None)
        val event = OrderPaymentAddressUpdated(orderId, paymentGroup.some, currentTime())

        eventLog.put(event.id, event)
          .bimap(
            err => failedEvent[PaymentGroup](orderId),
            evt => evt.asInstanceOf[OrderPaymentAddressUpdated]
          )
          /*_*/
          .fold(fail => Future.successful(fail), success => Future.successful(success))
          /*_*/

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
        /*_*/
        Future.successful(failedEvent[Order](id))
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
        val result: Future[Option[String]] =
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
