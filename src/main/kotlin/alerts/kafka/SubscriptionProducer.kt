package alerts.kafka

import alerts.env.Env
import alerts.persistence.Repository
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromAutoCloseable
import com.github.avrokotlin.avro4k.AvroNamespace
import io.github.nomisRev.kafka.KafkaProducer
import io.github.nomisRev.kafka.sendAwait
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@Serializable
@AvroNamespace("alerts.domain.subscription")
data class SubscriptionKey(val repository: Repository)

@Serializable
@AvroNamespace("alerts.domain.subscription")
data class SubscriptionEventRecord(val event: SubscriptionEvent)

@Serializable
enum class SubscriptionEvent {
  Created, Deleted;
}

context(ResourceScope)
suspend fun SubscriptionProducer(kafka: Env.Kafka): SubscriptionProducer {
  val settings = kafka.producer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
  val producer = Resource.fromAutoCloseable { KafkaProducer(settings) }.bind()
  return DefaultSubscriptionProducer(producer, kafka.subscriptionTopic)
}

interface SubscriptionProducer {
  suspend fun publish(repo: Repository): Unit
  suspend fun delete(repo: Repository): Unit
}

class DefaultSubscriptionProducer(
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
