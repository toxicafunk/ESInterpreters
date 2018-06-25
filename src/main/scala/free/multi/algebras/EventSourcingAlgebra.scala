package free.multi.algebras

import cats.InjectK
import cats.free.Free
import common.models.Input
import io.circe.Json

sealed trait EvenSourcingAlgebra[+T]

final case class ParseMessage(msg: String) extends EvenSourcingAlgebra[Option[Json]]

final case class HandleCommand(json: Json) extends EvenSourcingAlgebra[Option[Input]]

//final case class CreateEvent[F[_]](key: String, entity: Input, ordersCtx: Orders[F]) extends EvenSourcingAlgebra[OrderEvent[_]]

class EventSource[F[_]](implicit I: InjectK[EvenSourcingAlgebra, F]) {

  def parseMessage(msg: String): Free[F, Option[Json]] =
    Free.inject[EvenSourcingAlgebra, F](ParseMessage(msg))

  def handleCommand(json: Json): Free[F, Option[Input]] =
    Free.inject[EvenSourcingAlgebra, F](HandleCommand(json))

  /*def createEvent(key: String, entity: Input, ordersCtx: Orders[F]): Free[F, OrderEvent[_]] =
    Free.inject[EvenSourcingAlgebra, F](CreateEvent(key, entity, ordersCtx))*/
}

object EventSource {
  implicit def eventsource[F[_]](implicit I: InjectK[EvenSourcingAlgebra, F]): EventSource[F] =
    new EventSource[F]
}
