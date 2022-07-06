package alerts.env

import alerts.programs.Notifications
import alerts.programs.Subscriptions
import alerts.kafka.SubscriptionProducer
import alerts.kafka.githubEventProcessor
import alerts.persistence.subscriptionsPersistence
import alerts.persistence.userPersistence
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import mu.KotlinLogging

class Dependencies(
  val notifications: Notifications,
  val subscriptions: Subscriptions,
) {
  companion object {
    fun resource(env: Env): Resource<Dependencies> = resource {
      val sqlDelight = sqlDelight(env.postgres).bind()
      val users = userPersistence(sqlDelight.usersQueries)
      val subscriptionsPersistence =
        subscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
      val githubEventProcessor = githubEventProcessor(env.kafka)
      val producer = SubscriptionProducer.resource(env.kafka).bind()
      val subscriptions = Subscriptions(subscriptionsPersistence, users, producer)
      
      Dependencies(
        Notifications(users, subscriptionsPersistence, githubEventProcessor, KotlinLogging.logger { }),
        subscriptions
      )
    }
  }
}


