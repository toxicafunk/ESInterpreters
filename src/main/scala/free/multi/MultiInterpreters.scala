package free.multi

import java.time.Instant
import java.util.concurrent.{ExecutorService, Executors}

import cats.data.EitherK
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

    val eventLog: EventStore[String] = InMemoryEventStore.apply[String]

    def currentTime: Long = Instant.now().toEpochMilli

    def projection(events: List[Event[_]]): Order = {
      events.foldRight(Order("", List.empty, None))((evt, order) => evt.asInstanceOf[OrderEvent[_, _]].entity match {
        case None => order
        case Some(enty) => enty match {
          case o@Order(_, _, _) => o
          case c@CommerceItem(_, _, _) => order.copy(commerceItems = c +: order.commerceItems)
          case p@PaymentGroup(_, _, _, _) => order.copy(paymentGroup = p.some)
        }
      })
    }

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

      case AddCommerceItem(id, product, qty) => {
        val provider = RestClient.callProvider(product.providerId).unsafeRunSync()
        val section = provider.sections
          .filter(section => section.section == product.categoryId)
          .head

        val updatedEvents: Iterable[Either[Error, Event[_]]] = product.subProducts.map(entry => {
          val store = entry._2.platformId.map(p => {
            println(p);
            RestClient.callStore(p).unsafeRunSync()
          })
          val id = entry._1 + product.ean.getOrElse("")
          val commerceItem = CommerceItem(id, store.map(_.tightFlowIndicator), section.hasLogisticMargin)
          OrderCommerceItemUpdated(id, commerceItem.some, currentTime)
        })
          .map(hpu => eventLog.put(hpu.id, hpu))

        Future.successful(updatedEvents.map {
          _ match {
            case Left(err) => OrderUpdateFailed[CommerceItem, Product](id, None, product, err, currentTime)
            case Right(event) => event.asInstanceOf[OrderEvent[CommerceItem, Product]]
          }
        }.toStream)
      }

      case AddPaymentMethod(orderId, paymentMethod) => {
        val store = paymentMethod.platformId.map(RestClient.callStore(_).unsafeRunSync())
        val paymentGroup = PaymentGroup(orderId, store.flatMap(_.logistic).getOrElse(""), None, paymentMethod.some)
        val event = OrderPaymentGroupUpdated(orderId, paymentGroup.some, currentTime)
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

      case Replay(id, offset, event) => ???

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
