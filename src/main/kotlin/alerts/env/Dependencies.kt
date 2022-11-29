package alerts.env

import alerts.github.EnvGithubEventProcessor
import alerts.github.GithubClient
import alerts.subscription.SubscriptionProducer
import alerts.notification.NotificationService
import alerts.user.SqlDelightUserPersistence
import alerts.subscription.SqlDelightSubscriptionsPersistence
import alerts.subscription.SubscriptionService
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

class Dependencies(
  val notifications: NotificationService,
  val subscriptions: SubscriptionService,
  val metrics: PrometheusMeterRegistry,
)

context(ResourceScope)
suspend fun Dependencies(env: Env): Dependencies {
  val sqlDelight = SqlDelight(env.postgres)
  val appMicrometerRegistry = metrics()
  
  val slackUsersCounter: Counter =
    Counter
      .build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(appMicrometerRegistry.prometheusRegistry)
  
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

context(ResourceScope)
private suspend fun metrics(): PrometheusMeterRegistry =
  Resource({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _ -> p.close() }.bind()
