package free.multi.interpreters

import java.time.Instant

import cats.~>
import cats.implicits._
import common.RestClient
import common.models._
import events._
import free.multi.algebras._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class OrdersInterpreters(val eventLog: EventStore[String]) {

  val futureOrdersInterpreter = new (OrdersAlgebra ~> Future) {


    def failedEvent[O <: Output]: String => OrderEvent[O] = (id: String) => OrderUpdateFailed[Input, O](id, None, JsonOrder(id, List.empty, None), "Interpreter failed!", Instant.now().toEpochMilli)

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
        val provider = Try(RestClient.callProvider(product.providerId).unsafeRunSync()) match {
          case Failure(ex) => {
            println(ex)
            Provider("-1", List.empty)
          }
          case Success(p) => p
        }
        val section = provider.sections
          .filter(section => section.section == product.categoryId)
          .get(0).getOrElse(ProvidedSection("Section not found", false))

        val store = subProduct.platformId.map(p => {
          Try(RestClient.callStore(p).unsafeRunSync()) match {
            case Failure(ex) => {
              println(ex)
              Store("-1", "", None)
            }
            case Success(s) => s
          }
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
        val store = paymentMethod.platformId.map { platformId =>
          Try(RestClient.callStore(platformId).unsafeRunSync()) match {
            case Failure(ex) => {
              println(ex)
              Store("-1", "", None)
            }
            case Success(s) => s
          }
        }
        val method = paymentMethod match {
          case c@Credit(_, _, _, _) => c
          case p@PayPal(_, _, _, _) => p
        }
        val paymentGroup = PaymentGroup(orderId, store.flatMap(_.logistic).getOrElse(""), None, method.some)
        val event = OrderPaymentGroupUpdated(orderId, paymentGroup.some, currentTime())

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

      case UnknownCommand(id) => {
        val msg = s"Unknown command for $id"
        println(msg);
        /*_*/
        Future.successful(failedEvent[Order](id))
        /*_*/
      }
    }
  }
}
