package free.multi

import cats.free.Free
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

  def parseCommand(json: Json)(implicit ordersCtx: Orders[MessagingAndOrdersAlg]) = {
    val key = (json \\ "key").head.asString.get
    val command = (json \\ "command").head.asString.get
    val j: Json = json.\\("entity").head
    command match {
      case "createOrder" =>
        val o = j.as[Order]
        ordersCtx.createOrder(key, o.right.get)

      case "addCommerceItem" => j.as[Product] match {
        case Right(product) => ordersCtx.addCommerceItem(key, product, 0)
        case Left(err) => println(err);
          ordersCtx.unknownCommand(key)
      }
      case "addPaymentAddress" => j.as[Address] match {
        case Right(address) => ordersCtx.addPaymentAddress(key, address)
        case Left(err) => println(err);
          ordersCtx.unknownCommand(key)
      }
      case "addPaymentGroup" => j.as[Credit] match {
        case Right(paymentMethod) => ordersCtx.addPaymentGroup(key, paymentMethod)
        case Left(err) => println(err);
          ordersCtx.unknownCommand(key)
      }
      case "replay" => j.as[ReplayMsg] match {
        case Right(replay) => ordersCtx.replay(key, replay)
        case Left(err) => println(err);
          ordersCtx.unknownCommand(key)
      }
      case _ =>
        println("Unrecognized command")
        ordersCtx.unknownCommand(key)
    }
  }

  def processMessage[A <: BaseEntity](brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, Stream[String]] = {

    msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
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
    }
  }
}
