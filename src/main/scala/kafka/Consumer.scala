package kafka

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ExecutorService, Executors}
import java.util.{Collections, Properties}

import collection.JavaConverters._
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

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
      println(s"Subscribed to topic $topic on ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
      while (true) {
        val records: ConsumerRecords[String, String] = consumer.synchronized {
          consumer poll 1000
        }
        records.forEach { record =>
          println(s"Received ${records.count()} messages")
          val q = atomicQueue.get
          q.enqueue(record)
          atomicQueue.set(q)
        }
      }
    })
  }

  def replay(offset: Long): Unit = {
    println(s"Getting topic partitions for $topic - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
    consumer.synchronized {
      val partitionInfos = asScalaBuffer(consumer.partitionsFor(topic)).toList
      val topicPartitions = partitionInfos.map(partition => new TopicPartition(topic, partition.partition()))
      println("Pausing consumer")
      consumer.pause(asJavaCollection(topicPartitions))
      topicPartitions foreach { partition => {
        println(s"Topic partition: $partition")
        consumer.seek(partition, offset)
      }}
      consumer.resume(asJavaCollection(topicPartitions))
    }
  }

  def close(): Unit = consumer.synchronized {
      if (consumer != null) {
        println(s"Closing consumer - ${Thread.currentThread().getName} ${Thread.currentThread().getId}")
        consumer.close()
      }
    }

  def shutdown(): Unit = {
    println("Shutting down")
    close()
    if (executor != null) {
      println("Closing executor")
      executor.shutdown()
    }
    println("Shut down")
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
