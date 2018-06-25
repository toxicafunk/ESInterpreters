package free.multi.interpreters

import java.time.Instant

import cats.~>
import cats.implicits._
import common.models._
import events.OrderUpdateFailed
import free.multi.algebras._
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.Future

class EventSourcingInterpreters {

  val failedEvent = (id:String) => OrderUpdateFailed[Input, Output](id, None, JsonOrder(id, List.empty, None),"Interpreter failed!", Instant.now().toEpochMilli)

  val futureEventSourceInterpreter = new (EvenSourcingAlgebra ~> Future) {

    override def apply[A](fa: EvenSourcingAlgebra[A]): Future[A] = fa match {
      case ParseMessage(msg) => {
        val j: Json = parse(msg) match {
          case Left(err) => err.message.asJson
          case Right(j) => j
        }
        Future.successful(j.some)
      }

      case HandleCommand(json) => {
        val entity = {
          val key = (json \\ "key").head.asString
          val j = json.\\("entity").head
          val command = (json \\ "command").head.asString.getOrElse("")
          command match {
            case "createOrder" =>
              j.as[JsonOrder] match {
                case Left(err) => JsonOrder(key.getOrElse(err.message), List.empty, None)
                case Right(o1) => o1
              }
            case "addCommerceItem" => j.as[Product] match {
              case Left(err) => Product(err.getLocalizedMessage, err.message, None, "-99", Map.empty)
              case Right(p) => p
            }
            case "addPaymentAddress" => j.as[Address] match {
              case Left(err) => Address(err.getLocalizedMessage, err.message, -1)
              case Right(a) => a
            }
            case "addPaymentGroup" => j.as[Credit] match {
              case Left(err) => Credit(err.getLocalizedMessage, err.message, "", None)
              case Right(credit) => credit
            }
            case _ => JsonOrder("Unknown command", List.empty, None)
          }
        }
        Future.successful(entity.some)
      }

      /*case CreateEvent(key, entity, ordersCtx) => {
        val currentTime = () => Instant.now().toEpochMilli
        val r = entity match {
          case order@JsonOrder(id, _, _) => ordersCtx.createOrder(key, order).map(_.toOutput)
          case address@Address(id, _, _) => ordersCtx.addPaymentAddress(key, address).map(_.toOutput)
          case paymentMethod@Credit(id, _, _, _) => ordersCtx.addPaymentMethod(key, paymentMethod).map(_.toOutput)
          case paymentMethod@PayPal(id, _, _, _) => ordersCtx.addPaymentMethod(key, paymentMethod).map(_.toOutput)
          case product@Product(id, _, _, _, _) => ordersCtx.addCommerceItem(key, product.subProducts.toList.head._2, product, 1).map(_.toOutput)
          case _ => Free.pure[F, OrderEvent[Output]](failedEvent("Unknown command"))
        }
      }*/
    }
  }

}
