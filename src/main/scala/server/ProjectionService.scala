package server

import cats.effect.IO
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
      Ok(eventLog.get(id).head match {
        case oc@OrderCreated(_, _, _) => oc.projection.asJson
        case ocu@OrderCommerceItemUpdated(_, _, _) => ocu.projection.asJson
        case opu@OrderPaymentGroupUpdated(_, _, _) => opu.projection.asJson
        case opa@OrderPaymentAddressUpdated(_,_,_) => opa.projection.asJson
      })
    }
  }
}
