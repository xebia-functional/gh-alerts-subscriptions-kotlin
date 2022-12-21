package alerts.env

import alerts.github.GithubEvent
import alerts.github.SlackNotification
import alerts.kafka.AvroSerializer
import alerts.subscription.SubscriptionEventRecord
import alerts.subscription.SubscriptionKey
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderOptions


@Configuration
@ComponentScan
@Suppress("TooManyFunctions")
class AppConfig {

  @Bean
  fun registry(): PrometheusMeterRegistry =
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

  @Bean
  fun slackUsersCounter(meterRegistry: PrometheusMeterRegistry): Counter =
    Counter
      .build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(meterRegistry.prometheusRegistry)

  @Bean
  fun scope(): CoroutineScope = SpringScope(Dispatchers.IO)

  @Bean
  fun client(config: Github): WebClient =
    WebClient.create(config.url)

  @Bean
  fun clock(): Clock = Clock.System

  @Bean
  fun timeZone(): TimeZone = TimeZone.UTC

  @Bean
  fun eventReceiverSettings(kafka: Kafka): ReceiverOptions<Nothing, GithubEvent> =
    ReceiverOptions.create<Nothing, GithubEvent>(kafka.consumerProperties())
      .withValueDeserializer(AvroSerializer(GithubEvent.serializer()))
      .withKeyDeserializer(NothingDeserializer)
      .subscription(listOf(kafka.event.name))

  @Bean
  fun githubEventKafkaTemplate(
    options: ReceiverOptions<Nothing, GithubEvent>
  ): ReactiveKafkaConsumerTemplate<Nothing, GithubEvent> =
    ReactiveKafkaConsumerTemplate(options)

  @Bean
  @Qualifier("notification")
  fun notificationProducerSettings(kafka: Kafka): SenderOptions<Nothing, SlackNotification> =
    SenderOptions.create<Nothing, SlackNotification>(kafka.producerProperties())
      .withValueSerializer(AvroSerializer(SlackNotification.serializer()))
      .withKeySerializer(NothingSerializer)

  @Bean
  @Qualifier("notification")
  fun notificationKafkaTemplate(
    @Qualifier("notification") options: SenderOptions<Nothing, SlackNotification>
  ): ReactiveKafkaProducerTemplate<Nothing, SlackNotification> =
    ReactiveKafkaProducerTemplate(options)

  @Bean
  fun subscriptionProducerSettings(kafka: Kafka): SenderOptions<SubscriptionKey, SubscriptionEventRecord> =
    SenderOptions.create<SubscriptionKey, SubscriptionEventRecord>(kafka.producerProperties())
      .withValueSerializer(AvroSerializer(SubscriptionEventRecord.serializer()))
      .withKeySerializer(AvroSerializer(SubscriptionKey.serializer()))

  @Bean
  fun subscriptionKafkaTemplate(
    options: SenderOptions<SubscriptionKey, SubscriptionEventRecord>
  ): ReactiveKafkaProducerTemplate<SubscriptionKey, SubscriptionEventRecord> =
    ReactiveKafkaProducerTemplate(options)

  @Bean
  fun admin(kafka: Kafka): KafkaAdmin =
    KafkaAdmin(mapOf(BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers))

  @Bean
  fun notificationTopic(kafka: Kafka): NewTopic =
    TopicBuilder.name(kafka.notification.name)
      .partitions(kafka.notification.numPartitions)
      .replicas(kafka.notification.replicationFactor.toInt())
      .build()

  @Bean
  fun eventTopic(kafka: Kafka): NewTopic =
    TopicBuilder.name(kafka.event.name)
      .partitions(kafka.event.numPartitions)
      .replicas(kafka.event.replicationFactor.toInt())
      .build()

  @Bean
  fun subscriptionTopic(kafka: Kafka): NewTopic =
    TopicBuilder.name(kafka.subscription.name)
      .partitions(kafka.subscription.numPartitions)
      .replicas(kafka.subscription.replicationFactor.toInt())
      .build()
}

private object NothingSerializer : Serializer<Nothing> {
  override fun serialize(topic: String?, data: Nothing?): ByteArray = ByteArray(0)
}

private object NothingDeserializer : Deserializer<Nothing> {
  override fun deserialize(topic: String?, data: ByteArray?): Nothing =
    TODO()
}
