package server

import cats.effect.IO
import events._
import free.multi.MultiInterpreters

import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object EventService {

  val eventLog = MultiInterpreters.futureOrdersInterpreter.eventLog

  val service = HttpService[IO] {
    case GET -> Root / id => {
      println(s"id: $id")
      val lst = eventLog.get(id).map { _ match {
        case oc@OrderCreated(_, _, _) => oc.asJson
        case ocu@OrderCommerceItemUpdated(_, _, _) => ocu.asJson
        case opu@OrderPaymentGroupUpdated(_, _, _) => opu.asJson
      }}.asJson
      Ok(lst)
    }
  }
}
