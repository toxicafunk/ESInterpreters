package free.multi

import java.time.Instant

import cats.free.Free

import common.models.{Input, Output, _}
import events.{EventStore, OrderEvent, OrderUpdateFailed}
import free.multi.Algebras.MessagingAndOrdersAndESAlg
import free.multi.algebras._
import io.circe.generic.auto._
import io.circe.syntax._


object Programs {

  def replay(offset: Long)(implicit msgCtx: Messages[MessagingAndOrdersAndESAlg]): Free[MessagingAndOrdersAndESAlg, String] = {
    println("Replaying...")
    msgCtx.replay("", ReplayMsg("", offset, "")).flatMap(_ => {
      println(s"Offset $offset")
      Free.pure(s"Replayed from offset $offset")
    })
  }

  val failedEvent = (id:String) => OrderUpdateFailed[Input, Output](id, None, JsonOrder(id, List.empty, None),"Interpreter failed!", Instant.now().toEpochMilli)

  type FreeMsgEvents[O <: Output] = Free[MessagingAndOrdersAndESAlg, OrderEvent[O]]

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                 (implicit msgCtx: Messages[MessagingAndOrdersAndESAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAndESAlg],
                                      esCtx: EventSource[MessagingAndOrdersAndESAlg],
                                      eventLog: EventStore[String]): Free[MessagingAndOrdersAndESAlg, Stream[String]] =
    for {
      message <- msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit)
      json <- esCtx.parseMessage(message.getOrElse(""))
      key = json.flatMap(j => (j \\ "key").head.asString)
      entity <- esCtx.handleCommand(json.getOrElse({}.asJson))
      eventStream <- entity match {
              case Some(e) => e match {
                case order@JsonOrder(id, _, _) => ordersCtx.createOrder(key.getOrElse(id), order).map(Stream(_))
                case address@Address(id, _, _) => ordersCtx.addPaymentAddress(key.getOrElse(id), address).map(Stream(_))
                case paymentMethod@Credit(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(Stream(_))
                case paymentMethod@PayPal(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(Stream(_))
                case product@Product(id, _, _, _, _) => {
                  val streamFree = product.subProducts.values.toStream.map(
                    subProduct => ordersCtx.addCommerceItem(key.getOrElse(id), subProduct, product, 1)
                  )
                  streamSequence(streamFree)
                }
                case _ => Free.pure[MessagingAndOrdersAndESAlg, OrderEvent[Output]](failedEvent("Unknown command")).map(Stream(_))
              }
              case None => Free.pure[MessagingAndOrdersAndESAlg, OrderEvent[Output]](failedEvent("Unknown command")).map(Stream(_))
            }
      out <- {
        val res: Stream[Free[MessagingAndOrdersAndESAlg, String]] = eventStream.map(event => msgCtx.sendMessage(brokers, topic, event.projection.asJson.noSpaces))
        streamSequence(res)
      }
    } yield out
}
