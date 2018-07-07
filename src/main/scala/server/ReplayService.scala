package server

import cats.effect.IO
import cats.implicits._
import free.multi.MultiInterpreters

import free.multi.Programs.replay
import org.http4s.HttpService
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ReplayService {

  val service = (interpreters: MultiInterpreters) => HttpService[IO] {
    case GET -> Root / offset => {
      println(s"offset: $offset")
      val result: Future[String] = replay(offset.toLong).foldMap(interpreters.futureESOrMessagingOrReportInterpreter)
      val ioFut: IO[Future[String]] = IO(result)
      println(ioFut)
      Ok(IO.fromFuture(ioFut))
    }
  }
}
