package alerts.subscription

import alerts.env.Env
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromAutoCloseable
import io.github.nomisRev.kafka.KafkaProducer
import io.github.nomisRev.kafka.sendAwait
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

interface SubscriptionProducer {
  suspend fun publish(repo: Repository): Unit
  suspend fun delete(repo: Repository): Unit
}

fun SubscriptionProducer(kafka: Env.Kafka): Resource<SubscriptionProducer> =
  resource {
    val settings = kafka.producer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
    val producer = Resource.fromAutoCloseable { KafkaProducer(settings) }.bind()
    DefaultSubscriptionProducer(producer, kafka.subscriptionTopic)
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
