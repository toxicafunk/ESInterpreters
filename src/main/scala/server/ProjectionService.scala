package server

import cats.effect.IO
import common.models.Order
import events._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object ProjectionService {

  import free.multi.eventLog

  val service = HttpService[IO] {
    case GET -> Root / id => {
      println(s"id: $id")
      // head is last event
      Ok(eventLog.get(id).head.asInstanceOf[OrderEvent[Order]].projection.asJson)
    }
  }
}
