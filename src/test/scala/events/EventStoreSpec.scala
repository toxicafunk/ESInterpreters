package events

import cats.implicits._
import common.models.Order
import events.Data._
import free.multi.Programs._
import org.scalatest.Matchers._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class EventStoreSpec extends FlatSpec {

  "EvenStore" should "have the four events needed to create an order" in {
    messages.foreach { msg =>
      q.enqueue(msg)
      val result: Future[Option[String]] =
        processMessage("", "", "", false)
          .foldMap(futureTestingESOrMessagingOrOrdersInterpreter)

      result.filter(_.nonEmpty).foreach(s => println(s"message processed: $s"))
      Thread.sleep(500L)
    }

    Thread.sleep(2000L)

    eventLog.events("O123").bimap(
      err => { println(err); List.empty },
      lst => {println(lst); lst.size shouldBe 4}
    )

    val orderedEvents = eventLog.get("O123").sortBy(_.at)
    orderedEvents.foreach(evt => println(evt.asInstanceOf[OrderEvent[Order]].projection))
  }
}
