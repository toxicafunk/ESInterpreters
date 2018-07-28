package server

import cats.effect.IO
import free.multi.MultiInterpreters
import fs2.StreamApp.ExitCode
import fs2.{Stream, StreamApp}
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Properties.envOrNone

object Server extends StreamApp[IO] with nl.grons.metrics4.scala.DefaultInstrumented {
  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  println(s"Starting server on port $port")

  import com.codahale.metrics.jmx.JmxReporter

  val reporter = JmxReporter.forRegistry(metrics.registry).build
  reporter.start()

  // Define a health check
  //healthCheck("alive") { workerThreadIsActive() }

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    val interpreter = new MultiInterpreters(eventLog)
    interpreter.run()
    BlazeBuilder[IO]
      .bindHttp(port)
      .mountService(StaticService.service, "/")
      .mountService(EventService.service(interpreter, metrics.histogram("events-result")), "/events")
      .mountService(AllEventsService.service(interpreter), "/allevents")
      .mountService(ProjectionService.service(
        interpreter,
        metrics.timer("projection"),
        metrics.meter("getRequests", "requests")
      ), "/projection")
      .mountService(ReplayService.service(interpreter, metrics.counter("replays")), "/replay")
      .serve
  }
}