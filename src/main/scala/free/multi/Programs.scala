package free.multi

import java.time.Instant

import cats.implicits._
import cats.free.Free
import common.models.{Input, Output}
import events.{OrderCreated, OrderEvent, OrderUpdateFailed}
import common.models._
import free.multi.Algebras.{Messages, MessagingAndOrdersAlg, Orders}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object Programs {

  def replay(offset: Long)(implicit ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, String] = {
    println("Replaying...")
    ordersCtx.replay("", ReplayMsg("", offset, "")).flatMap(_ => {
      println(s"Offset $offset")
      Free.pure(s"Replayed from offset $offset")
    })
  }

  def handleCommand[F[_]](jsonOpt: Option[Json]): Free[F, Option[Input]] = {
    val entity = jsonOpt.map(json => {
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
        case "replay" => j.as[ReplayMsg] match {
          case Left(err) => ReplayMsg(err.getLocalizedMessage, -1, err.message)
          case Right(replayMsg) => replayMsg
        }
        case _ => JsonOrder("Unknown command", List.empty, None)
      }
    })
    Free.pure(entity)
  }

  def parseMessage[F[_]](msg: Option[String]): Free[F, Option[Json]] = {
    val j: Option[Json] = msg.map(m => parse(m) match {
      case Left(err) => err.message.asJson
      case Right(j) => j
    })
    Free.pure(j)
  }

  val currentTime = () => Instant.now().toEpochMilli

  def testOpt[F[_]]: Free[F, Option[OrderEvent[Output]]] = Free.pure(OrderCreated("123", Order("234", List.empty[CommerceItem], None).some, currentTime()).some)
  def test[F[_]]: Free[F, OrderEvent[Output]] = Free.pure(OrderCreated("123", Order("234", List.empty[CommerceItem], None).some, currentTime()))

  def createEvent[F[_]](key: Option[String], entityOpt: Option[Input], ordersCtx: Orders[F]): Option[Free[F,  OrderEvent[Output]]] = {
    if (entityOpt.isEmpty) None
    else {
      val entity = entityOpt.get
      val r = entity match {
        case order@JsonOrder(id, _, _) => ordersCtx.createOrder(key.getOrElse(id), order).map(_.toOutput)
        case address@Address(id, _, _) => ordersCtx.addPaymentAddress(key.getOrElse(id), address).map(_.toOutput)
        case paymentMethod@Credit(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(_.toOutput)
        case paymentMethod@PayPal(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(_.toOutput)
        case product@Product(id, _, _, _, _) => ordersCtx.addCommerceItem(key.getOrElse(id), product.subProducts.toList.head._2, product, 1).map(_.toOutput)
        case _ => Free.pure[F, OrderEvent[Output]](failedEvent("Unknown command"))
      }
      r.some
    }
  }

  val failedEvent = (id:String) => OrderUpdateFailed[Input, Output](id, None, JsonOrder(id, List.empty, None),"Interpreter failed!", Instant.now().toEpochMilli)

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, Option[String]] =
    for {
      message <- msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit)
      json <- parseMessage[MessagingAndOrdersAlg](message)
      key = json.flatMap(j => (j \\ "key").head.asString)
      entity <- handleCommand[MessagingAndOrdersAlg](json)
      event <- createEvent[MessagingAndOrdersAlg](key, entity, ordersCtx)
        .getOrElse(Free.pure[MessagingAndOrdersAlg, OrderEvent[Output]](failedEvent(entity.get.id)))
    } yield event.projection.asJson.noSpaces.some
}
