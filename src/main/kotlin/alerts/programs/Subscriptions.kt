package alerts.programs

import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserPersistence

class Subscriptions(
  private val subscriptions: SubscriptionsPersistence,
  private val users: UserPersistence,
  private val producer: SubscriptionProducer,
) {
  suspend fun findAll(slackUserId: SlackUserId): List<Subscription> =
    users.findSlackUser(slackUserId)
      ?.let { user -> subscriptions.findAll(user.userId) }.orEmpty()
  
  suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription) {
    val user = users.findOrInsertSlackUser(slackUserId)
    subscriptions.subscribe(user.userId, subscription)
    producer.publish(subscription.repository)
  }
  
  suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository) {
    users.findSlackUser(slackUserId)?.let { user ->
      subscriptions.unsubscribe(user.userId, repository)
      deleteSubscription(repository)
    }
  }
  
  private suspend fun deleteSubscription(repository: Repository) {
    val subscribers = subscriptions.findSubscribers(repository)
    if (subscribers.isEmpty()) producer.delete(repository)
  }
}
