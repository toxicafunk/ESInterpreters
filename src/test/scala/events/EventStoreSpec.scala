package events

import cats.implicits._

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
          .foldMap(futureTestingOrReportInterpreter)

      result.filter(_.nonEmpty).foreach(s => println(s"message processed: $s"))
    }
    Thread.sleep(5000L)
    eventLog.get("O123").size shouldBe 4
  }
}
