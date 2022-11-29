package alerts.subscription

import alerts.env.Env
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import io.github.nomisRev.kafka.KafkaProducer
import io.github.nomisRev.kafka.sendAwait
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

interface SubscriptionProducer {
  suspend fun publish(repo: Repository): Unit
  suspend fun delete(repo: Repository): Unit
}

suspend fun ResourceScope.SubscriptionProducer(kafka: Env.Kafka): SubscriptionProducer {
  val settings = kafka.producer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
  val producer = autoCloseable { KafkaProducer(settings) }
  return DefaultSubscriptionProducer(producer, kafka.subscriptionTopic)
}

private class DefaultSubscriptionProducer(
  private val producer: KafkaProducer<SubscriptionKey, SubscriptionEventRecord>,
  private val topic: Env.Kafka.Topic,
) : SubscriptionProducer {
  override suspend fun publish(repo: Repository): Unit {
    producer.sendAwait(
      ProducerRecord(
        topic.name,
        SubscriptionKey(repo),
        SubscriptionEventRecord(SubscriptionEvent.Created)
      )
    )
  }
  
  override suspend fun delete(repo: Repository) {
    producer.sendAwait(
      ProducerRecord(
        topic.name,
        SubscriptionKey(repo),
        SubscriptionEventRecord(SubscriptionEvent.Deleted)
      )
    )
  }
}
