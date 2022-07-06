package alerts.kafka

import alerts.KafkaContainer
import alerts.domain.SubscriptionEvent
import alerts.persistence.Repository
import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.AutoOffsetReset
import io.github.nomisRev.kafka.ConsumerSettings
import io.github.nomisRev.kafka.KafkaConsumer
import io.github.nomisRev.kafka.createTopic
import io.github.nomisRev.kafka.kafkaConsumer
import io.github.nomisRev.kafka.offsets
import io.github.nomisRev.kafka.subscribeTo
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.apache.kafka.clients.admin.NewTopic

class SubscriptionProducerSpec : StringSpec({
  val kafka by resource(KafkaContainer.resource())
  val admin by autoClose(lazy { Admin(AdminSettings(kafka.bootstrapServers)) })
  val producer by resource(arrow.fx.coroutines.continuations.resource {
    SubscriptionProducer.resource(kafka).bind()
  })
  
  val repo = Repository("owner", "name")
  val repo2 = Repository("owner-2", "name-2")
  
  beforeSpec {
    admin.createTopic(
      NewTopic(
        kafka.subscriptionTopic.name,
        kafka.subscriptionTopic.numPartitions,
        kafka.subscriptionTopic.replicationFactor
      )
    )
  }
  
  "Can produce value" {
    producer.publish(repo)
    val settings = ConsumerSettings(
      kafka.bootstrapServers,
      keyDeserializer = AvroSerializer(SubscriptionKey.serializer()),
      valueDeserializer = AvroSerializer(SubscriptionEventRecord.serializer()),
      groupId = "groupId",
      autoOffsetReset = AutoOffsetReset.Earliest
    )
    
    val record = kafkaConsumer(settings)
      .subscribeTo(kafka.subscriptionTopic.name)
      .take(1)
      .single()
    
    record.value().event shouldBe SubscriptionEvent.Created
    record.key().repository shouldBe repo
    
    KafkaConsumer(settings).use { consumer ->
      consumer.commitSync(record.offsets())
    }
  }
  
  "Can produce values" {
    producer.publish(repo)
    producer.publish(repo2)
    
    val settings = ConsumerSettings(
      kafka.bootstrapServers,
      keyDeserializer = AvroSerializer(SubscriptionKey.serializer()),
      valueDeserializer = AvroSerializer(SubscriptionEventRecord.serializer()),
      groupId = "groupId",
      autoOffsetReset = AutoOffsetReset.Earliest
    )
    
    val records = kafkaConsumer(settings)
      .subscribeTo(kafka.subscriptionTopic.name)
      .take(2)
      .toList()
    
    
    records[0].value().event shouldBe SubscriptionEvent.Created
    records[0].key().repository shouldBe repo
  
    records[1].value().event shouldBe SubscriptionEvent.Created
    records[1].key().repository shouldBe repo2
    
    KafkaConsumer(settings).use { consumer ->
      consumer.commitSync(records.offsets())
    }
  }
})
