package kafka

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ExecutorService, Executors}
import java.util.{Collections, Properties}

import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, ConsumerRecords, KafkaConsumer}

import scala.collection.mutable.Queue

class Consumer(val brokers: String,
               val topic: String,
               val groupId: String,
               val autoCommit: Boolean) {

  var executor: ExecutorService = Executors.newSingleThreadExecutor
  val props: Properties = createConsumerConfig(brokers, groupId, autoCommit)
  val consumer = new KafkaConsumer[String, String](props)
  val atomicQueue: AtomicReference[Queue[ConsumerRecord[String, String]]] =
    new AtomicReference[Queue[ConsumerRecord[String, String]]](Queue())


  def run(): Unit = {
    consumer.subscribe(Collections.singletonList(topic))

    executor.execute(() => {
      println(s"Subscribed to topic $topic on ${Thread.currentThread().getId}")
      while (true) {
        val records: ConsumerRecords[String, String] = consumer poll 1000
        records.forEach { record =>
          println(s"Received ${records.count()} messages")
          val q = atomicQueue.get
          q.enqueue(record)
          atomicQueue.set(q)
        }
      }
    })
  }

  def shutdown(): Unit = {
    if (consumer != null)
      consumer.close()
    if (executor != null)
      executor.shutdown()
  }

  def createConsumerConfig(brokers: String, groupId: String, autoCommit: Boolean): Properties = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString)
    props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    props
  }

  override def toString = s"Consumer(${atomicQueue.get()}, $brokers, $topic, $groupId)"
}
