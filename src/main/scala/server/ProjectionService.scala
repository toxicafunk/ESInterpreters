package server

import cats.effect.IO
import common.models.Order
import events._
import free.multi.MultiInterpreters
import io.circe.generic.auto._
import io.circe.syntax._
import nl.grons.metrics4.scala.{Meter, Timer}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

object ProjectionService {

  new MultiInterpreters(eventLog)

  val service = (interpreters: MultiInterpreters, timer: Timer, meter: Meter) => HttpService[IO] {
    case GET -> Root / id => {
      meter.mark()
      timer.time {
        // head is last event
        Ok(eventLog.get(id).head.asInstanceOf[OrderEvent[Order]].projection.asJson)
      }
    }
  }
}
