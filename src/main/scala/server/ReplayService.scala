package server

import cats.effect.IO
import common.models.ReplayMsg
import free.multi.MultiInterpreters
import nl.grons.metrics4.scala.Counter
import org.http4s.HttpService
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

import scala.concurrent.Future

object ReplayService {

  val service = (interpreters: MultiInterpreters, counter: Counter) => HttpService[IO] {
    case GET -> Root / offset => {
      counter += 1
      val result: Future[String] = interpreters.futureMessagingInterpreter.replay("", ReplayMsg("", offset.toLong, ""))
      Ok(IO.fromFuture(IO(result)))
    }
  }
}
