package free.multi

import cats.free.Free
import events.OrderEvent
//import cats.syntax.all._
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

  def parseOrder[F[_]](json: Json): Free[F, Order] = {
    println(json)
    val key = (json \\ "key").head.asString
    val j = json.\\("entity").head
    val command = (json \\ "command").head.noSpaces
    println(s"key: $key, command: $command, j:\n $j")
    val order = command match {
      case "createOrder" =>
        j.as[Order] match {
          case Left(err) => { println(err); Order(key.getOrElse(err.message), List.empty, None) }
          case Right(o1) => { println(o1); o1 }
        }
    }
    println(s"Order: $order")
    Free.pure(order)
  }

  def handleCommand[F[_]](msg: String): Free[F, Json] = {
    println(msg)
    val j: Json = parse(msg) match {
      case Left(err) => err.message.asJson
      case Right(j) => j
    }

    Free.pure(j)
  }


  /*def join[F[_], A <: BaseEntity](orderStream: Stream[Order], ordersCtx: Orders[F]): Free[F, Stream[OrderEvent[Order, Order]]] = {
    val tmp = orderStream.map(order => ordersCtx.createOrder(order.id, order))
    val tmp1 = tmp.flatMap(free => free.mapK(FunctionK.id[F]))
    tmp1
  }*/

  def join[F[_], A <: BaseEntity](order: Order, ordersCtx: Orders[F]): Free[F, OrderEvent[Order, Order]] = {
    println(order)
    ordersCtx.createOrder(order.id, order)
  }

  def processMessage[A <: BaseEntity](brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, String] =
    for {
      message <- msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit)
      json <- handleCommand[MessagingAndOrdersAlg](message)
      order <- parseOrder[MessagingAndOrdersAlg](json)
      event <- join[MessagingAndOrdersAlg, A](order, ordersCtx)
    } yield event.projection.asJson.noSpaces

  /*case "addCommerceItem" => val product = j.as[Product] match {
              case Left(err) => Product(err.getLocalizedMessage, err.message, None, "-99", Map.empty)
              case Right(p) => p
            }
              for {
                order <- ordersCtx.addCommerceItem(key, product, 0)
              } yield order
            case "addPaymentAddress" => val address = j.as[Address] match {
              case Left(err) => Address(err.getLocalizedMessage, err.message, -1)
              case Right(a) => a
            }
              for {
                order <- ordersCtx.addPaymentAddress(key, address)
              } yield order
            case "addPaymentGroup" => val paymentMethod = j.as[Credit] match {
              case Left(err) => Credit(err.getLocalizedMessage, err.message, "", None)
              case Right(credit) => credit
            }
              for {
                order <- ordersCtx.addPaymentGroup(key, paymentMethod)
              } yield order
            case "replay" => val replay = j.as[ReplayMsg] match {
              case Left(err) => ReplayMsg(err.getLocalizedMessage, -1, err.message)
              case Right(replayMsg) => replayMsg
            }
              for {
                order <- ordersCtx.replay(key, replay)
              } yield order
            case _ => for {
              order <- ordersCtx.unknownCommand(key)
            } yield order*/

  /*msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Stream.Empty)
      case Stream(message: String) => parse(message) match {
        case Left(error) => {
          println(error);
          Free.pure(Stream.Empty)
        }
        case Right(json) => {
          val eventStream = parseCommand(json)
          if (!autoCommit) msgCtx.commit() else ()
          eventStream.map(stream => stream.map(event => {
            val msg = event.projection.asJson.noSpaces
            val topicView = topic + "View"
            println(s"$brokers - $topicView - $msg")
            msgCtx.sendMessage(brokers, topicView, msg).flatMap(str => Free.pure(println(str)))
            msg
          }))
        }
      }
    }*/
}
