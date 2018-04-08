package free

import cats.implicits._
import cats.~>
import free.Algebras._
import kafka.{Consumer, Producer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Interpreters extends App {

  val futureInterpreter = new (MessagingAlgebra ~> Future) {

    var consumerOpt: Option[Consumer] = None

    override def apply[A](fa: MessagingAlgebra[A]): Future[A] = fa match {
      case ReceiveMessage(brokers, topic, consumerGroup, autoCommit) => {
        consumerOpt = consumerOpt orElse {
          val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
          cons.run()
          Some(cons)
        }

        consumerOpt match {
          case None => Future(Stream.empty)
          case Some(c) => Future(c.atomicQueue.get().toStream.map(record => record.value()))
        }
      }

      case Commit() => Future.successful(consumerOpt.get.consumer.commitSync())

      case SendMessage(brokers, topic, message) => {
        val producer = new Producer(brokers)
        Future.successful(producer.sendMessage(topic, "", message))
      }
    }
  }

  import Programs._

  while (true) {
    val result: Future[Either[Unit, String]] =
      processMessage("192.168.99.100:9092", "test", "testers", false).foldMap(futureInterpreter)
    result.foreach {
      case Left(_) => ()
      case Right(s) => println(s"message processed: $s")
    }
    Thread.sleep(2000)
  }
}
