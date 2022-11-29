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
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

class Dependencies(
  val notifications: NotificationService,
  val subscriptions: SubscriptionService,
  val metrics: PrometheusMeterRegistry,
)

suspend fun ResourceScope.Dependencies(env: Env): Dependencies {
  val appMicrometerRegistry = metricsRegistry()
  val slackUsersCounter: Counter = slackUsersCounter(appMicrometerRegistry)
  
  val sqlDelight = SqlDelight(env.postgres)
  val users = SqlDelightUserPersistence(sqlDelight.usersQueries, slackUsersCounter)
  val subscriptionsPersistence =
    SqlDelightSubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  
  val githubEventProcessor = EnvGithubEventProcessor(env.kafka)
  val producer = SubscriptionProducer(env.kafka)
  
  val client = GithubClient(env.github)
  
  return Dependencies(
    NotificationService(users, subscriptionsPersistence, githubEventProcessor),
    SubscriptionService(subscriptionsPersistence, users, producer, client),
    appMicrometerRegistry
  )
}
