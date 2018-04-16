package server

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import java.io.File

object StaticService {
  val service = HttpService[IO] {
    case request@GET -> Root / name =>
      StaticFile.fromFile(new File(s"dist/$name"), Some(request))
        .getOrElseF(NotFound()) // In case the file doesn't exist
  }
}