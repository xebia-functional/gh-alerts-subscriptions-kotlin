package alerts.env

import alerts.github.EnvGithubEventProcessor
import alerts.github.GithubClient
import alerts.metrics.metricsRegistry
import alerts.metrics.slackUsersCounter
import alerts.notification.NotificationService
import alerts.subscription.SqlDelightSubscriptionsPersistence
import alerts.subscription.SubscriptionProducer
import alerts.subscription.SubscriptionService
import alerts.user.SqlDelightUserPersistence
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

class Dependencies(
  val notifications: NotificationService,
  val subscriptions: SubscriptionService,
  val metrics: PrometheusMeterRegistry,
)

fun Dependencies(env: Env): Resource<Dependencies> = resource {
  val appMicrometerRegistry = metricsRegistry.bind()
  val slackUsersCounter: Counter = slackUsersCounter(appMicrometerRegistry)
  
  val sqlDelight = SqlDelight(env.postgres).bind()
  val users = SqlDelightUserPersistence(sqlDelight.usersQueries, slackUsersCounter)
  val subscriptionsPersistence =
    SqlDelightSubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  
  val githubEventProcessor = EnvGithubEventProcessor(env.kafka)
  val producer = SubscriptionProducer(env.kafka).bind()
  
  val client = GithubClient(env.github).bind()
  
  Dependencies(
    NotificationService(users, subscriptionsPersistence, githubEventProcessor),
    SubscriptionService(subscriptionsPersistence, users, producer, client),
    appMicrometerRegistry
  )
}
