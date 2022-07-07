package alerts.kafka

import alerts.KafkaContainer
import alerts.persistence.Repository
import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.AutoOffsetReset
import io.github.nomisRev.kafka.ConsumerSettings
import io.github.nomisRev.kafka.KafkaConsumer
import io.github.nomisRev.kafka.commitBatchWithin
import io.github.nomisRev.kafka.createTopic
import io.github.nomisRev.kafka.kafkaConsumer
import io.github.nomisRev.kafka.offsets
import io.github.nomisRev.kafka.component1
import io.github.nomisRev.kafka.component2
import io.github.nomisRev.kafka.subscribeTo
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.apache.kafka.clients.admin.NewTopic
import kotlin.time.Duration.Companion.seconds

class SubscriptionProducerSpec : StringSpec({
  val kafka by resource(KafkaContainer.resource())
  val admin by autoClose(lazy { Admin(AdminSettings(kafka.bootstrapServers)) })
  val producer by resource(arrow.fx.coroutines.continuations.resource {
    SubscriptionProducer.resource(kafka).bind()
  })
  val settings by lazy {
    ConsumerSettings(
      kafka.bootstrapServers,
      keyDeserializer = AvroSerializer(SubscriptionKey.serializer()),
      valueDeserializer = AvroSerializer(SubscriptionEventRecord.serializer()),
      groupId = "groupId",
      autoOffsetReset = AutoOffsetReset.Earliest
    )
  }
  
  beforeSpec {
    admin.createTopic(
      NewTopic(
        kafka.subscriptionTopic.name,
        kafka.subscriptionTopic.numPartitions,
        kafka.subscriptionTopic.replicationFactor
      )
    )
  }
  
  val repo = Repository("owner", "name")
  
  "Can publish repo" {
    producer.publish(repo)
    
    kafkaConsumer(settings)
      .subscribeTo(kafka.subscriptionTopic.name)
      .take(1)
      .onEach { (key, value) ->
        value.event shouldBe SubscriptionEvent.Created
        key.repository shouldBe repo
      }.commitBatchWithin(settings, 1, 15.seconds)
      .collect()
  }
  
  "Can delete repo" {
    producer.delete(repo)
    
    kafkaConsumer(settings)
      .subscribeTo(kafka.subscriptionTopic.name)
      .take(1)
      .onEach { (key, value) ->
        value.event shouldBe SubscriptionEvent.Deleted
        key.repository shouldBe repo
      }.commitBatchWithin(settings, 1, 15.seconds)
      .collect()
  }
  
  "Can publish and then delete values" {
    producer.publish(repo)
    producer.delete(repo)
    
    val records = kafkaConsumer(settings)
      .subscribeTo(kafka.subscriptionTopic.name)
      .take(2)
      .toList()
    
    records.associate { Pair(it.key().repository, it.value().event) } shouldBe mapOf(
      repo to SubscriptionEvent.Created,
      repo to SubscriptionEvent.Deleted
    )
    
    KafkaConsumer(settings).use { consumer ->
      consumer.commitSync(records.offsets())
    }
  }
})