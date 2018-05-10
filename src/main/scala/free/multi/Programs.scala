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

  def processMessage[A <: BaseEntity](brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, Stream[String]] = {
    val jsonStream: Free[MessagingAndOrdersAlg, Stream[String]] = msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Stream.Empty)
      case Stream(message: String) => parse(message) match {
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
            case "replay" => j.as[ReplayMsg] match {
              case Right(replay) => ordersCtx.replay(key, replay)
              case Left(err) => println(err); ordersCtx.unknownCommand(key)
            }
            case _ =>
              println("Unrecognized command")
              ordersCtx.unknownCommand(key)
          }

          println("Stage 1 completed")
          evts.map(eventStream => {
            if (!autoCommit)
              msgCtx.commit()
            println(eventStream)
            eventStream.map(event => {
              val json = event.projection.asJson.noSpaces
              println(json)
              json
            })
          })
      }
    }

    jsonStream.flatMap { str =>
      str.flatMap(json =>
        msgCtx.sendMessage(brokers, topic + "View", json).flatMap(() => Free.pure(json))
      )
    }
  }
}
