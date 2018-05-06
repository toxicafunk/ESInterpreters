package server

import cats.effect.IO
import free.multi.MultiInterpreters
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Properties.envOrNone

object Server extends StreamApp[IO] {
  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  MultiInterpreters.run()
  println(s"Starting server on port $port")

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(port)
      .mountService(StaticService.service, "/")
      .mountService(EventService.service, "/events")
      .mountService(ProjectionService.service, "/projection")
      .mountService(ReplayService.service, "/replay")
      .serve
}