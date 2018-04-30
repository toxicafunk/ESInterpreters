package free

import cats.free.Free
import common.models._
import free.Algebras._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object Programs {

  def processMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): MessagingService[Either[Unit, String]] =
    receiveMessage(brokers, topic, consumerGroup, autoCommit).flatMap {
      case Stream.Empty => Free.pure(Left(()))
      case Stream(message) => decode[Product](message) match {
        case Left(_) => Free.pure(Left(()))
        case Right(product) =>
          if (!autoCommit)
            commit()

          val json = product.asJson.noSpaces
          sendMessage(brokers, topic + "View", json)
            .map(_ => Right(json))
      }

    }

}
