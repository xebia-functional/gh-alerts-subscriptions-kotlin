package alerts.subscription

import alerts.env.SubscriptionTopic
import kotlinx.coroutines.reactive.awaitSingle
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Component

interface SubscriptionProducer {
  suspend fun publish(repo: Repository)
  suspend fun delete(repo: Repository)
}

@Component
class DefaultSubscriptionProducer(
  private val producer: ReactiveKafkaProducerTemplate<SubscriptionKey, SubscriptionEventRecord>,
  private val topic: SubscriptionTopic,
) : SubscriptionProducer {
  override suspend fun publish(repo: Repository) {
    producer.send(
      ProducerRecord(
        topic.name,
        SubscriptionKey(repo),
        SubscriptionEventRecord(SubscriptionEvent.Created)
      )
    ).awaitSingle()
  }

  override suspend fun delete(repo: Repository) {
    producer.send(
      ProducerRecord(
        topic.name,
        SubscriptionKey(repo),
        SubscriptionEventRecord(SubscriptionEvent.Deleted)
      )
    ).awaitSingle()
  }
}
