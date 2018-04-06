package free

import Algebras._
import common.models._
import cats.free.Free
import io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.Error

object Programs {

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): MessagingService[Either[Unit, String]] =
    receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Left(()))
      case Stream(message) => {
        decode[Product](message) match {
          case Left(error) => Free.pure(Left(()))
          case Right(product) => {
            if (!autoCommit)
              commit()

            val json = product.asJson.noSpaces
            sendMessage(brokers, topic + "View", json)
              .map(_ => Right(json))
          }
        }
      }
    }

}
