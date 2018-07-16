package server

import cats.effect.IO
import common.models.ReplayMsg
import free.multi.MultiInterpreters
import org.http4s.HttpService
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

import scala.concurrent.Future

object ReplayService {

  val service = (interpreters: MultiInterpreters) => HttpService[IO] {
    case GET -> Root / offset => {
      println(s"offset: $offset")
      val result: Future[String] = interpreters.futureMessagingInterpreter.replay("", ReplayMsg("", offset.toLong, ""))
      val ioFut: IO[Future[String]] = IO(result)
      println(ioFut)
      Ok(IO.fromFuture(ioFut))
    }
  }
}
