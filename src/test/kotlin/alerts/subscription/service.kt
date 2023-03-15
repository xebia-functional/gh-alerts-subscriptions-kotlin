package alerts.subscription

import alerts.IntegrationTestBase
import alerts.env.AppConfig
import alerts.env.Kafka
import alerts.env.receiverOptions
import alerts.github.GithubError
import alerts.kafka.AvroSerializer
import alerts.now
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
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import java.util.*

class SubscriptionServiceSpec(
    private val subscriptions: SubscriptionsPersistence,
    private val users: UserPersistence,
    private val userRepo: UserRepo,
    private val kafka: Kafka,
    private val appConfig: AppConfig
) : IntegrationTestBase({

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

fun producer(
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

fun consumer(
    kafka: Kafka
): ReactiveKafkaConsumerTemplate<SubscriptionKey, SubscriptionEventRecord> =
    with(kafka) {
        ReactiveKafkaConsumerTemplate(
            receiverOptions(
                subscription.name,
                consumerProperties(),
                SubscriptionKey.serializer(),
                SubscriptionEventRecord.serializer()
            )
        )
    }

private suspend fun Kafka.committedMessages(
    kafkaAdmin: KafkaAdmin
): Long =
    withConsumer(
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

private suspend fun <K, V, A> withConsumer(
    properties: Properties,
    keyDeserializer: KSerializer<K>,
    valueDeserializer: KSerializer<V>,
    action: suspend KafkaConsumer<K, V>.(KafkaConsumer<K, V>) -> A
): A =
    KafkaConsumer(properties, AvroSerializer(keyDeserializer), AvroSerializer(valueDeserializer))
        .use { action(it, it) }
