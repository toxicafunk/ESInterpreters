package free.multi

import java.util.concurrent.{ExecutorService, Executors}

import cats.implicits._
import events._
import free.multi.interpreters.{MessagingInterpreters, OrdersInterpreters, EventSourcingInterpreters}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MultiInterpreters(val eventLog: EventStore[String]) {

  val futureMessagingOrReportInterpreter = new MessagingInterpreters().futureMessagingInterpreter or
    new OrdersInterpreters(eventLog).futureOrdersInterpreter

  val futureESOrMessagingOrReportInterpreter = new EventSourcingInterpreters().futureEventSourceInterpreter or futureMessagingOrReportInterpreter

  import Programs._

  var executor: ExecutorService = Executors.newSingleThreadExecutor

  def run(): Unit = {
    executor.execute(() => {
      import free.multi.algebras.Messages.messages
      import free.multi.algebras.Orders.reports
      import free.multi.algebras.EventSource.eventsource
      println(s"Interpreter executing on thread ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
      while (true) {
        val result: Future[Stream[String]] =
          processMessage("192.168.99.100:9092", "test", "testers", false)(messages, reports, eventsource, eventLog)
            .foldMap(futureESOrMessagingOrReportInterpreter)

        result.filter(_.nonEmpty).foreach(s => println(s"message processed: $s"))
        Thread.sleep(2000L)
      }
    })
  }

  def shutdown(): Unit = {
    if (executor != null)
      executor.shutdown()
  }

}
