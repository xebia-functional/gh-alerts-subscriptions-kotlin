package alerts.kafka

import alerts.KafkaContainer
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import alerts.install
import alerts.invoke
import alerts.subscription.Repository
import alerts.subscription.SubscriptionEvent
import alerts.subscription.SubscriptionEventRecord
import alerts.subscription.SubscriptionKey
import alerts.subscription.SubscriptionProducer
import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.arrow.fx.coroutines.extension
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class SubscriptionProducerSpec : StringSpec({
  val kafka = install { KafkaContainer() }
  val producer = install { SubscriptionProducer(kafka()) }
  val settings = install {
    kafka().consumer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
  }
  
  val repo = Repository("owner", "name")
  
  "Can publish repo" {
    producer().publish(repo)
    KafkaReceiver(settings())
      .receive(kafka().subscriptionTopic.name)
      .take(1)
      .onEach { record ->
        record.value().event shouldBe SubscriptionEvent.Created
        record.key().repository shouldBe repo
        record.offset.acknowledge()
      }.collect()
  }
  
  "Can delete repo" {
    producer().delete(repo)
    
    KafkaReceiver(settings())
      .receive(kafka().subscriptionTopic.name)
      .take(1)
      .onEach { record ->
        record.value().event shouldBe SubscriptionEvent.Deleted
        record.key().repository shouldBe repo
        record.offset.acknowledge()
      }.collect()
  }
  
  "Can publish and then delete values" {
    producer().publish(repo)
    producer().delete(repo)
    
    val records = KafkaReceiver(settings())
      .receiveAutoAck(kafka().subscriptionTopic.name)
      .flatMapConcat { it }
      .take(2)
      .toList()
    
    records.associate { Pair(it.key().repository, it.value().event) } shouldBe mapOf(
      repo to SubscriptionEvent.Created,
      repo to SubscriptionEvent.Deleted
    )
  }
})
