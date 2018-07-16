package free.multi.algebras

import common.models.Input
import io.circe.Json

trait EventSourcingAlgebra[F[_]] {
  def parseMessage(msg: String): F[Option[Json]]
  def handleCommand(json: Json): F[Option[Input]]
}
