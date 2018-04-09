package free.multi

import cats.free.Free
import common.models._
import events.{OrderCommerceItemUpdated, OrderEvent}
import free.multi.Algebras.{Messages, MessagingAndOrdersAlg, Orders, Reports}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

//import io.circe.generic.semiauto._

object Programs {

  // TODO: decoder/encoder for generic HapromEvent
  //implicit val fooDecoder: Decoder[Foo] = deriveDecoder[Foo]
  //implicit val fooEncoder: Encoder[Foo] = deriveEncoder[Foo]

  def processMessage[A <: BaseEntity](brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                    (implicit msgCtx: Messages[MessagingAndOrdersAlg],
                     repCtx: Orders[MessagingAndOrdersAlg]): Free[MessagingAndOrdersAlg, Stream[String]] =
    msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Stream.Empty)
      case Stream(message) => {
        decode[Message[A]](message) match {
          case Left(error) => {
            println(error)
            Free.pure(Stream.Empty)
          }
          case Right(msg) => {
            val evts = msg.entity match {
              case None => repCtx.createOrder(msg.key)
              case Some(entity) => entity match {
                case p@Product(id, categoryId, ean, providerId, subProducts) => repCtx.addCommerceItem(id, p, 0)
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
