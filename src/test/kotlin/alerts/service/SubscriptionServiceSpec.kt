package alerts.service

import alerts.IntegrationTestBase
import alerts.env.AppConfig
import alerts.env.EventTopic
import alerts.env.Kafka
import alerts.env.NotificationTopic
import alerts.env.SubscriptionTopic
import alerts.github.GithubError
import alerts.kafka.AvroSerializer
import alerts.subscription.DefaultSubscriptionProducer
import alerts.subscription.RepoNotFound
import alerts.subscription.Repository
import alerts.subscription.SpringSubscriptionService
import alerts.subscription.Subscription
import alerts.subscription.SubscriptionEvent
import alerts.subscription.SubscriptionEventRecord
import alerts.subscription.SubscriptionKey
import alerts.subscription.SubscriptionsPersistence
import alerts.user.SlackUserId
import alerts.user.UserPersistence
import alerts.user.UserRepo
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import reactor.kafka.receiver.ReceiverOptions
import java.util.*

class SubscriptionServiceSpec(
    private val subscriptions: SubscriptionsPersistence,
    private val users: UserPersistence,
    private val kafkaConfig: Kafka,
    private val userRepo: UserRepo,
    private val appConfig: AppConfig
) : IntegrationTestBase({

    val kafka = with(kafkaConfig) { kafka(subscription, event, notification) }
    val producer = producer(appConfig, kafka)

    val slackUserId = SlackUserId("test-user-id")
    val subscription = Subscription(Repository("arrow-kt", "arrow"), LocalDateTime.now())

    afterTest {
        userRepo.deleteAll()
    }

    "If repo exists, and repo has no subscribers then event is send to Kafka" {
        users.insertSlackUser(slackUserId)
        val service = SpringSubscriptionService(subscriptions, users, producer) { _, _ -> true.right() }

        service.subscribe(slackUserId, subscription).shouldBeRight(Unit)

        val record = consumer(kafka).receive().asFlow().firstOrNull().shouldNotBeNull()

        record.key() shouldBe SubscriptionKey(subscription.repository)
        record.value() shouldBe SubscriptionEventRecord(SubscriptionEvent.Created)
    }

    "If repo exists, and repo has subscribers then no event is sent to Kafka" {
        val user = users.insertSlackUser(slackUserId)
        subscriptions.subscribe(user.userId, subscription)
        val service = SpringSubscriptionService(subscriptions, users, producer) { _, _ -> true.right() }

        service.subscribe(slackUserId, subscription).shouldBeRight(Unit)

        val kafkaAdmin = with(appConfig) { admin(kafka) }
        kafka.committedMessages(kafkaAdmin) shouldBe 0
    }

    "If repo doesn't exist, it returns RepoNotFound" {
        users.insertSlackUser(slackUserId)
        val service = SpringSubscriptionService(subscriptions, users, producer) { _, _ -> false.right() }

        service.subscribe(slackUserId, subscription).shouldBeLeft(RepoNotFound(subscription.repository))
    }

    "If Github Client returns unexpected StatusCode, it returns RepoNotFound with StatusCode" {
        users.insertSlackUser(slackUserId)
        val service = SpringSubscriptionService(subscriptions, users, producer) { _, _ ->
            GithubError(HttpStatus.BAD_GATEWAY).left()
        }

        service.subscribe(slackUserId, subscription)
            .shouldBeLeft(RepoNotFound(subscription.repository, HttpStatus.BAD_GATEWAY))
    }
})

private fun kafka(
    subscription: SubscriptionTopic,
    event: EventTopic,
    notification: NotificationTopic
) = with(IntegrationTestBase.container) {
    Kafka(getBootstrapServers(), getSchemaRegistryUrl(), subscription, event, notification)
}

private fun producer(
    appConfig: AppConfig, kafka: Kafka
): DefaultSubscriptionProducer =
    with(appConfig) {
        DefaultSubscriptionProducer(
            subscriptionKafkaTemplate(
                subscriptionProducerSettings(kafka)
            ),
            kafka.subscription
        )
    }

private fun consumer(kafka: Kafka): ReactiveKafkaConsumerTemplate<SubscriptionKey, SubscriptionEventRecord> =
    with(kafka) {
        ReactiveKafkaConsumerTemplate(
            createReceiverOptions(
                subscription.name,
                consumerProperties(),
                SubscriptionKey.serializer(),
                SubscriptionEventRecord.serializer()
            )
        )
    }

private suspend fun Kafka.committedMessages(
    kafkaAdmin: KafkaAdmin
): Long = withConsumer(
    consumerProperties(),
    SubscriptionKey.serializer(),
    SubscriptionEventRecord.serializer()
) { consumer ->
    val description = kafkaAdmin.describeTopics(subscription.name)[subscription.name]
    val partitions = description?.partitions().orEmpty().map { info ->
        TopicPartition(subscription.name, info.partition())
    }.toSet()

    consumer.committed(partitions).mapNotNull { (_, offset) ->
        offset?.takeIf { it.offset() > 0 }?.offset()
    }.sum()
}

suspend fun <K, V, A> withConsumer(
    properties: Properties,
    keyDeserializer: KSerializer<K>,
    valueDeserializer: KSerializer<V>,
    action: suspend KafkaConsumer<K, V>.(KafkaConsumer<K, V>) -> A
): A =
    KafkaConsumer(properties, AvroSerializer(keyDeserializer), AvroSerializer(valueDeserializer))
        .use { action(it, it) }


private fun <K, V> createReceiverOptions(
    topic: String,
    properties: Properties,
    keyDeserializer: KSerializer<K>,
    valueDeserializer: KSerializer<V>
): ReceiverOptions<K, V> =
    ReceiverOptions.create<K, V>(properties).subscription(listOf(topic))
        .withKeyDeserializer(AvroSerializer(keyDeserializer))
        .withValueDeserializer(AvroSerializer(valueDeserializer))

private fun LocalDateTime.Companion.now(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.UTC)
