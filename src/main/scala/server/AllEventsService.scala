package server

import cats.effect.IO
import events._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object AllEventsService {

  import free.multi.eventLog

  val service: HttpService[IO] = HttpService[IO] {
    case GET -> Root =>
      val events = eventLog.allEvents
      println(events)
      val jsons = events.getOrElse(List.empty).map {
        case oc@OrderCreated(_, _, _) => oc.asJson
        case ocu@OrderCommerceItemUpdated(_, _, _) => ocu.asJson
        case opu@OrderPaymentGroupUpdated(_, _, _) => opu.asJson
        case opa@OrderPaymentAddressUpdated(_,_,_) => opa.asJson
        case _ => {}.asJson
      }.asJson
      Ok(jsons)
  }
}
