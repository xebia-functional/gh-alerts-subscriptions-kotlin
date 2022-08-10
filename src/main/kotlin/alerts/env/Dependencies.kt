package alerts.env

import alerts.https.client.GithubClient
import alerts.kafka.SubscriptionProducer
import alerts.kafka.githubEventProcessor
import alerts.persistence.subscriptionsPersistence
import alerts.persistence.userPersistence
import alerts.service.NotificationService
import alerts.service.SubscriptionService
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

class Dependencies(
  val notifications: NotificationService,
  val subscriptions: SubscriptionService,
  val metrics: PrometheusMeterRegistry,
) {
  companion object {
    fun resource(env: Env): Resource<Dependencies> = resource {
      val sqlDelight = sqlDelight(env.postgres).bind()
      val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
      
      val slackUsersCounter: Counter =
        Counter
          .build()
          .name("slack_users_counter")
          .help("Number of Slack users registered with our service")
          .register(appMicrometerRegistry.prometheusRegistry)
      
      val users = userPersistence(sqlDelight.usersQueries, slackUsersCounter)
      val subscriptionsPersistence =
        subscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
      val githubEventProcessor = githubEventProcessor(env.kafka)
      val producer = SubscriptionProducer.resource(env.kafka).bind()
      val client = GithubClient.resource(env.github).bind()
      Dependencies(
        NotificationService(users, subscriptionsPersistence, githubEventProcessor),
        SubscriptionService(subscriptionsPersistence, users, producer, client),
        appMicrometerRegistry
      )
    }
  }
}
