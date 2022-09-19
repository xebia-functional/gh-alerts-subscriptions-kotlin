package alerts.service

import alerts.KafkaContainer
import alerts.PostgreSQLContainer
import alerts.TestMetrics
import alerts.env.Env
import alerts.env.SqlDelight
import alerts.github.GithubError
import alerts.subscription.SubscriptionEvent
import alerts.subscription.SubscriptionEventRecord
import alerts.subscription.SubscriptionKey
import alerts.subscription.SubscriptionProducer
import alerts.subscription.Repository
import alerts.user.SlackUserId
import alerts.subscription.Subscription
import alerts.subscription.SubscriptionsPersistence
import alerts.subscription.RepoNotFound
import alerts.subscription.SqlDelightSubscriptionsPersistence
import alerts.subscription.SubscriptionService
import alerts.user.SqlDelightUserPersistence
import alerts.user.UserPersistence
import arrow.core.left
import arrow.core.right
import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.describeTopic
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.kafka.common.TopicPartition

class SubscriptionServiceSpec : StringSpec({
  val kafka by resource(KafkaContainer.resource())
  val postgres by resource(PostgreSQLContainer.resource())
  val sqlDelight by resource(SqlDelight(postgres.config()))
  val producer by resource(SubscriptionProducer(kafka))
  
  val subscriptions by lazy {
    SqlDelightSubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  }
  val users by lazy { SqlDelightUserPersistence(sqlDelight.usersQueries, TestMetrics.slackUsersCounter) }
  
  afterTest { postgres.clear() }
  
  val slackUserId = SlackUserId("test-user-id")
  val subscription = Subscription(Repository("arrow-kt", "arrow"), Clock.System.now().toLocalDateTime(TimeZone.UTC))
  
  "If repo exists, and repo has no subscribers then event is send to Kafka" {
    users.insertSlackUser(slackUserId)
    val service = SubscriptionService(subscriptions, users, producer) { _, _ -> true.right() }
    
    service.subscribe(slackUserId, subscription).shouldBeRight(Unit)
    
    val record = KafkaReceiver(
      kafka.consumer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
    ).receiveAutoAck(kafka.subscriptionTopic.name)
      .firstOrNull()?.firstOrNull().shouldNotBeNull() // First message of first batch
    
    record.key() shouldBe SubscriptionKey(subscription.repository)
    record.value() shouldBe SubscriptionEventRecord(SubscriptionEvent.Created)
  }
  
  "If repo exists, and repo has subscribers then no event is send to Kafka" {
    val user = users.insertSlackUser(slackUserId)
    subscriptions.subscribe(user.userId, subscription)
    val service = SubscriptionService(subscriptions, users, producer) { _, _ -> true.right() }
    
    service.subscribe(slackUserId, subscription).shouldBeRight(Unit)
    committedMessages(kafka) shouldBe 0
  }
  
  "If repo doesn't exist, it returns RepoNotFound" {
    users.insertSlackUser(slackUserId)
    val service = SubscriptionService(subscriptions, users, producer) { _, _ -> false.right() }
    
    service.subscribe(slackUserId, subscription).shouldBeLeft(RepoNotFound(subscription.repository))
  }
  
  "If Github Client returns unexpected StatusCode, it returns RepoNotFound with StatusCode" {
    users.insertSlackUser(slackUserId)
    val service =
      SubscriptionService(subscriptions, users, producer) { _, _ -> GithubError(HttpStatusCode.BadGateway).left() }
    
    service.subscribe(slackUserId, subscription)
      .shouldBeLeft(RepoNotFound(subscription.repository, HttpStatusCode.BadGateway))
  }
})

private suspend fun committedMessages(
  kafka: Env.Kafka,
): Long = KafkaReceiver(
  kafka.consumer(SubscriptionKey.serializer(), SubscriptionEventRecord.serializer())
).withConsumer {
  val partitions = Admin(AdminSettings(kafka.bootstrapServers)).use { admin ->
    val description = admin.describeTopic(kafka.subscriptionTopic.name)
    description?.partitions().orEmpty().map { info ->
      TopicPartition(kafka.subscriptionTopic.name, info.partition())
    }.toSet()
  }
  committed(partitions).mapNotNull { (_, offset) ->
    offset?.takeIf { it.offset() > 0 }?.offset()
  }.sum()
}
