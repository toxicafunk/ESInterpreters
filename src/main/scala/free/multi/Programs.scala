package free.multi

import cats.free.Free
import free.multi.Algebras.{Messages, MessagingAndReportAlg, Reports}
import common.models._
import events.{HapromEvent, HapromProductUpdated}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

//import io.circe.generic.semiauto._

object Programs {

  // TODO: decoder/encoder for generic HapromEvent
  //implicit val fooDecoder: Decoder[Foo] = deriveDecoder[Foo]
  //implicit val fooEncoder: Encoder[Foo] = deriveEncoder[Foo]

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                    (implicit msgCtx: Messages[MessagingAndReportAlg],
                     repCtx: Reports[MessagingAndReportAlg]): Free[MessagingAndReportAlg, Stream[String]] =
    msgCtx.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Stream.Empty)
      case Stream(message) => {
        decode[Product](message) match {
          case Left(error) => {
            println(error)
            Free.pure(Stream.Empty)
          }
          case Right(product) => {
            repCtx.updateHapromProduct(product.id, product, 0)
              .map( eventStream => {
                println(eventStream)
                if (!autoCommit)
                  msgCtx.commit()
                eventStream.map( event => {
                  val json = event.asInstanceOf[HapromProductUpdated].asJson.noSpaces
                  msgCtx.sendMessage(brokers, topic + "View", json)
                  json
                })
              })
          }
        }
      }
    }

}
