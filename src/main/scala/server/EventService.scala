package server

import cats.effect.IO
import events._
import free.multi.MultiInterpreters
import io.circe.generic.auto._
import io.circe.syntax._
import nl.grons.metrics4.scala.Histogram
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object EventService {

  val service = (interpreters: MultiInterpreters, hist: Histogram) => HttpService[IO] {

    case GET -> Root / id => {

      val events = eventLog.get(id)
      hist += events.size
      val jsons = events.toStream.map {
        case oc@OrderCreated(_, _, _) => oc.asJson
        case ocu@OrderCommerceItemUpdated(_, _, _) => ocu.asJson
        case opu@OrderPaymentGroupUpdated(_, _, _) => opu.asJson
        case opa@OrderPaymentAddressUpdated(_, _, _) => opa.asJson
        case _ => {}.asJson
      }.asJson
      Ok(jsons)

    }
  }
}
