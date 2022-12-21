package alerts.env

import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties("github")
data class Github @ConstructorBinding constructor(val url: String, val token: String?)

interface Topic {
  val name: String
  val numPartitions: Int
  val replicationFactor: Short
}

@ConfigurationProperties("kafka.subscription")
data class SubscriptionTopic @ConstructorBinding constructor(
  override val name: String,
  override val numPartitions: Int,
  override val replicationFactor: Short,
): Topic

@ConfigurationProperties("kafka.event")
data class EventTopic @ConstructorBinding constructor(
  override val name: String,
  override val numPartitions: Int,
  override val replicationFactor: Short,
): Topic

@ConfigurationProperties("kafka.notification")
data class NotificationTopic @ConstructorBinding constructor(
  override val name: String,
  override val numPartitions: Int,
  override val replicationFactor: Short,
): Topic

@ConfigurationProperties("kafka")
data class Kafka @ConstructorBinding constructor(
  val bootstrapServers: String,
  val schemaRegistryUrl: String,
  val subscription: SubscriptionTopic,
  val event: EventTopic,
  val notification: NotificationTopic,
) {
  private val eventConsumerGroupId = "github-event-consumer"

  fun consumerProperties() = Properties().apply {
    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ConsumerConfig.GROUP_ID_CONFIG, eventConsumerGroupId)
  }

  fun producerProperties() = Properties().apply {
    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
  }
}
