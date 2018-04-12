package free.multi

import cats.free.Free
import common.models._
import events.{OrderCommerceItemUpdated, OrderEvent}
import free.multi.Algebras.{Messages, MessagingAndOrdersAlg, Orders}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object Programs {

  /*implicit def messageDecoder[A<:BaseEntity:Decoder]: Decoder[Message[A]] = (c: HCursor) => {
    for {
      k <- c.downField("key").as[String]
      cmd <- c.downField("command").as[String]
      entity <- c.downField("entity").as[A]
      ts <- c.downField("timestamp").as[Long]
    } yield Message(k, entity, cmd, ts)
  }*/

  def processMessage[A <: BaseEntity](brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, Stream[String]] =
    msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Stream.Empty)
      case Stream(message: String) => {
        parse(message) match {
          case Left(error) => {
            println(error);
            Free.pure(Stream.Empty)
          }
          case Right(json) => {
            val key = (json \\("key")).head.asString.get
            val cmd = (json \\("command")).head.asString.get
            println(s"$cmd: $key")
            val j: Json = json.\\("entity").head
            val evts = cmd match {
              case "createOrder" => {
                val o = j.as[Order]
                println(s"Enter: $o");
                ordersCtx.createOrder(key, o.right.get)
              }
              case "addCommerceItem" => ordersCtx.addCommerceItem(key, j.as[Product].right.get, 0)
              case "addPaymentAddress" => ordersCtx.addPaymentAddress(key, j.as[Address].right.get)
              case "addPaymentGroup" => ordersCtx.addPaymentGroup(key, j.as[PaymentMethod].right.get)
              case _ => {
                println("Unrecognized command")
                ordersCtx.unknownCommand(key)
              }
            }

            evts.map(eventStream => {
              if (!autoCommit)
                msgCtx.commit()
              eventStream.map(event => {
                val json = event.asInstanceOf[OrderCommerceItemUpdated].asJson.noSpaces
                msgCtx.sendMessage(brokers, topic + "View", json)
                json
              })
            })
          }
        }
      }
    }

}
