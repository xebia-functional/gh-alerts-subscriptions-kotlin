package alerts.subscription

import alerts.IntegrationTestBase
import alerts.env.AppConfig
import alerts.env.Kafka
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow

class SubscriptionProducerSpec(
  private val kafka: Kafka,
  private val appConfig: AppConfig
) : IntegrationTestBase({

  val producer = producer(appConfig, kafka)

  val repo = Repository("owner", "name")
  
  "Can publish repo" {
    producer.publish(repo)

    consumer(kafka)
      .receive()
      .asFlow()
      .take(1)
      .onEach { record ->
        record.value().event shouldBe SubscriptionEvent.Created
        record.key().repository shouldBe repo
        record.receiverOffset().acknowledge()
      }.collect()
  }
  
  "Can delete repo" {
    producer.delete(repo)

    consumer(kafka)
      .receive()
      .asFlow()
      .take(1)
      .onEach { record ->
        record.value().event shouldBe SubscriptionEvent.Deleted
        record.key().repository shouldBe repo
        record.receiverOffset().acknowledge()
      }.collect()
  }
  
  "Can publish and then delete values" {
    producer.publish(repo)
    producer.delete(repo)

    val records = consumer(kafka)
      .receiveAutoAck()
      .asFlow()
      .take(2)
      .toList()
    
    records.associate { Pair(it.key().repository, it.value().event) } shouldBe mapOf(
      repo to SubscriptionEvent.Created,
      repo to SubscriptionEvent.Deleted
    )
  }
})
