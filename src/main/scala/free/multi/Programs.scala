package free.multi

import java.time.Instant

import cats.free.Free
import cats.implicits._
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

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                                     (implicit msgCtx: Messages[MessagingAndOrdersAndESAlg],
                                      ordersCtx: Orders[MessagingAndOrdersAndESAlg],
                                      esCtx: EventSource[MessagingAndOrdersAndESAlg],
                                      eventLog: EventStore[String]): Free[MessagingAndOrdersAndESAlg, Option[String]] =
    for {
      message <- msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit)
      json <- esCtx.parseMessage(message.getOrElse(""))
      key = json.flatMap(j => (j \\ "key").head.asString)
      entity <- esCtx.handleCommand(json.getOrElse({}.asJson))
      event <- entity match {
              case Some(e) => e match {
                case order@JsonOrder(id, _, _) => ordersCtx.createOrder(key.getOrElse(id), order).map(_.toOutput)
                case address@Address(id, _, _) => ordersCtx.addPaymentAddress(key.getOrElse(id), address).map(_.toOutput)
                case paymentMethod@Credit(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(_.toOutput)
                case paymentMethod@PayPal(id, _, _, _) => ordersCtx.addPaymentMethod(key.getOrElse(id), paymentMethod).map(_.toOutput)
                case product@Product(id, _, _, _, _) => ordersCtx.addCommerceItem(key.getOrElse(id), product.subProducts.toList.head._2, product, 1).map(_.toOutput)
                case _ => Free.pure[MessagingAndOrdersAndESAlg, OrderEvent[Output]](failedEvent("Unknown command"))
              }
              case None => Free.pure[MessagingAndOrdersAndESAlg, OrderEvent[Output]](failedEvent("Unknown command"))
            }
      out = event.projection.asJson.noSpaces
      _ <- msgCtx.sendMessage(brokers, topic, out)
    } yield out.some
}
