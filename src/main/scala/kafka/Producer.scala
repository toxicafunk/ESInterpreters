package kafka


import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

class Producer(val brokers: String) {

  val props = new Properties()
  props.put("bootstrap.servers", brokers)
  props.put("client.id", "ScalaProducerExample")
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val producer = new KafkaProducer[String, String](props)

  def sendMessage(topic: String, key: String, message: String) {
    val data = new ProducerRecord[String, String](topic, key, message)
    //async
    //producer.send(data, (m,e) => {})
    //sync
    producer.send(data)
  }

  def shutdown() = {
    producer.close()
  }
}