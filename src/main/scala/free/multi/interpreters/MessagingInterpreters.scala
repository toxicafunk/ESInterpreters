package free.multi.interpreters

import cats.~>
import free.multi.algebras._
import kafka.{Consumer, Producer}

import scala.concurrent.Future

class MessagingInterpreters {
  var consumerOpt: Option[Consumer] = None
  var producerOpt: Option[Producer] = None

  val futureMessagingInterpreter = new (MessagingAlgebra ~> Future) {

    override def apply[A](fa: MessagingAlgebra[A]): Future[A] = fa match {

      case Commit() => {
        println("Committing")
        /*_*/
        Future.successful(consumerOpt.get.consumer.commitSync())
        /*_*/
      }

      case SendMessage(brokers, topic, message) => {
        println(s"SendMessage: $brokers - $topic - $message")
        producerOpt = producerOpt.orElse(Some(new Producer(brokers)))
        /*_*/
        producerOpt.map(p => p.sendMessage(topic, "", message)).map(Future.successful).getOrElse(Future.successful("Error sending message"))
        /*_*/
      }

      case ReceiveMessage(brokers, topic, consumerGroup, autoCommit) =>
        consumerOpt = consumerOpt.orElse {
          val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
          cons.run()
          Some(cons)
        }

        /*_*/
        Future.successful {
          consumerOpt
            .map(c => {
              val q = c.atomicQueue.get()
              if (q.isEmpty) ""
              else q.dequeue().value()
            })
            .filter(s => !s.isEmpty)
        }
      /*_*/

      case Replay(_, replayMsg) => {
        println(s"Consumer is present ${consumerOpt.isDefined} - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
        consumerOpt.map(c => {
          println(s"Seeking offset ${replayMsg.offset}")
          c.replay(replayMsg.offset)
        })
        Future.unit
      }
    }
  }
}
