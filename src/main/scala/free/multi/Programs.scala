package free.multi

import java.time.Instant

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import common.models.{Input, Output, _}
import events.{EventStore, OrderUpdateFailed}
import free.multi.algebras._
import io.circe.generic.auto._
import io.circe.syntax._


class Programs[F[_] : Monad](msgCtx: MessagingAlgebra[F], ordersCtx: OrdersAlgebra[F], esCtx: EventSourcingAlgebra[F]) {

  def replay(offset: Long): F[String] = {
    val replayResult: F[String] = msgCtx.replay("", ReplayMsg("", offset, ""))
    replayResult.flatMap(_ => {
      implicitly[Monad[F]].pure(s"Replayed from offset $offset")
    })
  }

  val failedEvent = (id: String) => OrderUpdateFailed[Input, Output](id, None, JsonOrder(id, List.empty, None), "Interpreter failed!", Instant.now().toEpochMilli)


  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                    (implicit eventLog: EventStore[String]): F[Stream[String]] =
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
            streamFree.streamSequence
          }
          case _ => implicitly[Monad[F]].pure(failedEvent("Unknown command")).map(Stream(_))
        }
        case None => implicitly[Monad[F]].pure(failedEvent("Unknown command")).map(Stream(_))
      }
      out <- {
        val res: Stream[F[String]] =
          /*_*/
          eventStream.map(event => msgCtx.sendMessage(brokers, topic + "Out", event.projection.asJson.noSpaces))
          /*_*/
        res.streamSequence
      }
    } yield out
}
