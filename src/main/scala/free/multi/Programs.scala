package free.multi

import cats.free.Free
import common.models._
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
          case Right(json) =>
            val key = (json \\ "key").head.asString.get
            val cmd = (json \\ "command").head.asString.get
            val j: Json = json.\\("entity").head
            val evts = cmd match {
              case "createOrder" =>
                val o = j.as[Order]
                ordersCtx.createOrder(key, o.right.get)

              case "addCommerceItem" => j.as[Product] match {
                  case Right(product) => ordersCtx.addCommerceItem(key, product, 0)
                  case Left(err) => println(err); ordersCtx.unknownCommand(key)
                }
              case "addPaymentAddress" => j.as[Address] match {
                case Right(address) => ordersCtx.addPaymentAddress(key, address)
                case Left(err) => println(err); ordersCtx.unknownCommand(key)
              }
              case "addPaymentGroup" => j.as[Credit] match {
                case Right(paymentMethod) => ordersCtx.addPaymentGroup(key, paymentMethod)
                case Left(err) => println(err); ordersCtx.unknownCommand(key)
              }
              case _ =>
                println("Unrecognized command")
                ordersCtx.unknownCommand(key)
            }

            evts.map(eventStream => {
              if (!autoCommit)
                msgCtx.commit()
              eventStream.map(event => {
                val json = event.projection.asJson.noSpaces
                msgCtx.sendMessage(brokers, topic + "View", json)
                println(json)
                json
              })
            })
        }
      }
    }

}
