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
          case Some(c) => Future(c.atomicQueue.getAndSet(Queue.empty).toStream.map(_.value()))
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

    def currentTime: Long = Instant.now().toEpochMilli

    val st: Stream[String] = Stream.Empty
    st.isEmpty

    override def apply[A](fa: OrdersAlgebra[A]): Future[A] = fa match {

      case CreateOrder(id, order) => {
        val event = OrderCreated(id, Order(id, List.empty, None).some, currentTime)
        Future.successful {
          eventLog.put(id, event) match {
            case Left(err) => {
              println(err);
              Stream(OrderUpdateFailed(id, order.some, order, err, currentTime))
            }
            case Right(evt) => {
              println(evt)
              Stream(evt.asInstanceOf[OrderEvent[Order, Order]])
            }
          }
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

        Future.successful(updatedEvents.map {
          case Left(err) => OrderUpdateFailed[CommerceItem, Product](id, None, product, err, currentTime)
          case Right(event) => event.asInstanceOf[OrderEvent[CommerceItem, Product]]
        }.toStream)
      }

      case AddPaymentMethod(orderId, paymentMethod) => {
        val store = paymentMethod.platformId.map(RestClient.callStore(_).unsafeRunSync())
        println(s"Store: $store")
        val method = paymentMethod match {
          case c@Credit(_,_,_,_) => c
          case p@PayPal(_,_,_,_) => p
        }
        val paymentGroup = PaymentGroup(orderId, store.flatMap(_.logistic).getOrElse(""), None, method.some)
        val event = OrderPaymentGroupUpdated(orderId, paymentGroup.some, currentTime)
        println(event)
        Future.successful {
          eventLog.put(event.id, event) match {
            case Left(err) => Stream(OrderUpdateFailed[PaymentGroup, PaymentMethod](orderId, None, paymentMethod, err, currentTime))
            case Right(evt) => Stream(evt.asInstanceOf[OrderEvent[PaymentGroup, PaymentMethod]])
          }
        }
      }

      case AddPaymentAdress(orderId, address) => {
        val paymentGroup = PaymentGroup(orderId, "", address.some, None)
        val event = OrderPaymentAddressUpdated(orderId, paymentGroup.some, currentTime)
        Future.successful {
          eventLog.put(event.id, event) match {
            case Left(err) => Stream(OrderUpdateFailed(orderId, None, address, err, currentTime))
            case Right(evt) => Stream(evt.asInstanceOf[OrderEvent[PaymentGroup, Address]])
          }
        }
      }

      case Replay(id@_, offset@_, event@_) => ???

      case UnknownCommand(id) => { println(s"Unknown command for $id"); Future.successful(Stream.Empty) }
    }
  }

  val futureMessagingOrReportInterpreter = futureMessagingInterpreter or futureOrdersInterpreter

  import Programs._

  var executor: ExecutorService = Executors.newSingleThreadExecutor

  def run(): Unit = {
    executor.execute(() => {
      println(s"Interpreter executing on thread ${Thread.currentThread().getId}")
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
