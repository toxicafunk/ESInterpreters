package server

import cats.effect.IO
import events._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object EventService {

  import free.multi.eventLog

  val service: HttpService[IO] = HttpService[IO] {
    case GET -> Root / id =>
      println(s"id: $id")
      val events = eventLog.get(id)
      println(events)
      val jsons = events.toStream.map {
        case oc@OrderCreated(_, _, _) => oc.asJson
        case ocu@OrderCommerceItemUpdated(_, _, _) => ocu.asJson
        case opu@OrderPaymentGroupUpdated(_, _, _) => opu.asJson
        case opa@OrderPaymentAddressUpdated(_,_,_) => opa.asJson
      }.asJson
      Ok(jsons)
  }
}
