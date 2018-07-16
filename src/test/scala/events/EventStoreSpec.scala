package events

import cats.syntax.either._
import cats.instances.future._
import common.models.Order
import events.Data._
import free.multi.Programs
import free.multi.interpreters.{EventSourceInterpreter, OrdersInterpreter}
import org.scalatest.Matchers._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class EventStoreSpec extends FlatSpec {

  val eventStore: EventStore[String] = Data.eventLog

  val testingOrdersInterpreter: OrdersInterpreter = new OrdersInterpreter {
    override val eventLog: EventStore[String] = eventStore
  }
  val testingEventSourcingInterpreter: EventSourceInterpreter = new EventSourceInterpreter {}
  val testingMessagingInterpreter: TestingInterpreter = new TestingInterpreter {}

  "EvenStore" should "have the four events needed to create an order" in {
    messages.foreach { msg =>
      q.enqueue(msg)
      val result: Future[Stream[String]] = new Programs(testingMessagingInterpreter, testingOrdersInterpreter, testingEventSourcingInterpreter)
        .processMessage("", "", "", false)(eventStore)

      result.filter(_.nonEmpty).foreach(s => println(s"message processed: $s"))
      Thread.sleep(1000L)
    }

    Thread.sleep(2000L)
    println("\nFinished processing messages!\n")

    eventStore.events("O123").bimap(
      err => { println(err); List.empty },
      lst => lst.size shouldBe 5
    )

    println("Printing events for O123")
    val orderedEvents = eventLog.get("O123").sortBy(_.at)
    orderedEvents.foreach(evt => println(evt.asInstanceOf[OrderEvent[Order]].projection))
  }
}
