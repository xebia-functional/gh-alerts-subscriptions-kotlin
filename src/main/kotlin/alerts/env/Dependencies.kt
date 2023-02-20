package alerts.env

import alerts.github.EnvGithubEventProcessor
import alerts.github.GithubClient
import alerts.subscription.SubscriptionProducer
import alerts.metrics.metricsRegistry
import alerts.metrics.slackUsersCounter
import alerts.notification.NotificationService
import alerts.user.SqlDelightUserPersistence
import alerts.subscription.SqlDelightSubscriptionsPersistence
import alerts.subscription.SubscriptionService
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.kafka.KafkaClusterHealthCheck
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig

class Dependencies(
  val notifications: NotificationService,
  val subscriptions: SubscriptionService,
  val metrics: PrometheusMeterRegistry,
  val healthCheck: HealthCheckRegistry
)

suspend fun ResourceScope.Dependencies(env: Env): Dependencies {
  val appMicrometerRegistry = metricsRegistry()
  val slackUsersCounter: Counter = slackUsersCounter(appMicrometerRegistry)
  val dataSource = hikari(env.postgres)
  val sqlDelight = SqlDelight(dataSource)
  val users = SqlDelightUserPersistence(sqlDelight.usersQueries, slackUsersCounter)
  val subscriptionsPersistence =
    SqlDelightSubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  val githubEventProcessor = EnvGithubEventProcessor(env.kafka)
  val producer = SubscriptionProducer(env.kafka)
  val client = GithubClient(env.github)

  val kafkaHealthCheck = kafkaHealthCheck(env.kafka)
  val checks = HealthCheckRegistry(Dispatchers.Default) {
    register(HikariConnectionsHealthCheck(dataSource, 1), 5.seconds)
    register(kafkaHealthCheck, 5.seconds)
  }

  return Dependencies(
    NotificationService(users, subscriptionsPersistence, githubEventProcessor),
    SubscriptionService(subscriptionsPersistence, users, producer, client),
    appMicrometerRegistry,
    checks
  )
}
