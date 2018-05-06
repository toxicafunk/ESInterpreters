package server

import cats.effect.IO
import cats.implicits._
import free.multi.MultiInterpreters.futureMessagingOrReportInterpreter
import free.multi.Programs.replay
import org.http4s.HttpService
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext.Implicits.global

object ReplayService {

  val service = HttpService[IO] {
    case GET -> Root / offset => {
      println(s"offset: $offset")
      Ok(IO.fromFuture(IO.pure(replay(offset.toLong).foldMap(futureMessagingOrReportInterpreter))))
    }
  }
}
