package free.multi

import java.util.concurrent.{ExecutorService, Executors}

import cats.instances.future._

import events._
import free.multi.interpreters._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MultiInterpreters(val eventStore: EventStore[String]) {

  var executor: ExecutorService = Executors.newSingleThreadExecutor
  val futureMessagingInterpreter = new MessagingInterpreter {}
  val futureOrdersInterpreter = new OrdersInterpreter {
    override val eventLog: EventStore[String] = eventStore
  }
  val futureeEventSourcingInterpreter = new EventSourceInterpreter {}

  def run(): Unit = {
    executor.execute(() => {
      println(s"Interpreter executing on thread ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
      while (true) {
        val result: Future[Stream[String]] = new Programs(futureMessagingInterpreter, futureOrdersInterpreter, futureeEventSourcingInterpreter)
          .processMessage("172.18.0.3:9092", "test", "metrics", false)(eventStore)

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
