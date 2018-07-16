package free.multi.interpreters

import common.models
import free.multi.Id
import free.multi.algebras._
import kafka.{Consumer, Producer}

import scala.concurrent.Future

trait MessagingInterpreter extends MessagingAlgebra[Future] {

  var consumerOpt: Option[Consumer] = None
  var producerOpt: Option[Producer] = None

  override def commit(): Future[Unit] = {
    println("Committing")
    Future.successful(consumerOpt.get.consumer.commitSync())
  }

  override def sendMessage(brokers: String, topic: String, message: String): Future[String] = {
    println(s"SendMessage: $brokers - $topic - $message")
    producerOpt = producerOpt.orElse(Some(new Producer(brokers)))
    producerOpt.map(p => p.sendMessage(topic, "", message)).map(Future.successful).getOrElse(Future.successful("Error sending message"))
  }

  override def receiveMessage(brokers: String, topic: String, consumerGroup: String, autoCommit: Boolean): Future[Option[String]] = {
    consumerOpt = consumerOpt.orElse {
      val cons: Consumer = new Consumer(brokers, topic, consumerGroup, autoCommit)
      cons.run()
      Some(cons)
    }

    Future.successful {
      consumerOpt
        .map(c => {
          val q = c.atomicQueue.get()
          if (q.isEmpty) ""
          else q.dequeue().value()
        })
        .filter(s => !s.isEmpty)
    }
  }

  override def replay(id: Id, entity: models.ReplayMsg): Future[String] = {
    println(s"Consumer is present ${consumerOpt.isDefined} - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
    consumerOpt.map(c => {
      println(s"Seeking offset ${entity.offset}")
      c.replay(entity.offset)
    })
    Future.successful("Success!")
  }
}
