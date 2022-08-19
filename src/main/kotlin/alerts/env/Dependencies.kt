package alerts.env

import alerts.https.client.GithubClient
import alerts.kafka.SubscriptionProducer
import alerts.kafka.GithubEventProcessor
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.userPersistence
import alerts.service.NotificationService
import alerts.service.SubscriptionService
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
  val sqlDelight = sqlDelight(env.postgres)
  val appMicrometerRegistry = metrics()
  
  val slackUsersCounter: Counter =
    Counter
      .build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(appMicrometerRegistry.prometheusRegistry)
  
  val users = userPersistence(sqlDelight.usersQueries, slackUsersCounter)
  val subscriptionsPersistence =
    SubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  val githubEventProcessor = GithubEventProcessor(env.kafka)
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
