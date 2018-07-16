package free.multi.interpreters

import java.time.Instant

import cats.implicits._
import common.models._
import events.OrderUpdateFailed
import free.multi.algebras._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.Future

trait EventSourceInterpreter extends EventSourcingAlgebra[Future] {

  val failedEvent = (id: String) => OrderUpdateFailed[Input, Output](id, None, JsonOrder(id, List.empty, None), "Interpreter failed!", Instant.now().toEpochMilli)

  type JsonOpt = Option[Json]
  type InputOpt = Option[Input]

  override def parseMessage(msg: String): Future[JsonOpt] = {
    val j: Json = parse(msg) match {
      case Left(err) => err.message.asJson
      case Right(j) => j
    }
    Future.successful(j.some)
  }

  override def handleCommand(json: Json): Future[InputOpt] = {
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
}
