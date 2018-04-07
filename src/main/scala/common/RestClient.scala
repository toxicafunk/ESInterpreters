package common


import models.{Provider, Store}
import cats._, cats.effect._, cats.implicits._
import org.http4s.Uri
import org.http4s.client.blaze._
import org.http4s.circe._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global

object RestClient {
  val httpClient = Http1Client[IO]()

  def callProvider[T](id: String): IO[Provider] = {
    val target = Uri.uri("http://localhost:8080/provider/") / id
    httpClient.flatMap(_.expect(target)(jsonOf[IO, Provider]))
  }

  def callStore[T](id: String): IO[Store] = {
    val target = Uri.uri("http://localhost:8080/store/") / id
    httpClient.flatMap(_.expect(target)(jsonOf[IO, Store]))
  }
}
