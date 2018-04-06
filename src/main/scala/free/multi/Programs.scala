package free.multi

import cats.free.Free
import free.multi.Algebras.{Messages, MessagingAndReportAlg, Reports}
import common.models._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object Programs {

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean)
                    (implicit msg: Messages[MessagingAndReportAlg],
                     rep: Reports[MessagingAndReportAlg]): Free[MessagingAndReportAlg, Either[Unit, String]] =
    msg.receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Left(()))
      case Stream(message) => {
        decode[Product](message) match {
          case Left(error) => Free.pure(Left(println(error)))
          case Right(product) => {
            rep.updateHapromProduct(product.id, product, 0)
            if (!autoCommit)
              msg.commit()

            val json = product.asJson.noSpaces
            msg.sendMessage(brokers, topic + "View", json)
              .map(_ => Right(json))
          }
        }
      }
    }

}
