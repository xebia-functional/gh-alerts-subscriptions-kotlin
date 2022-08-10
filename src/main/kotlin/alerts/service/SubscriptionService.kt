package alerts.service

import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserPersistence

interface SubscriptionService {
  suspend fun findAll(slackUserId: SlackUserId): List<Subscription>
  suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription)
  suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository)
}

fun SubscriptionService(
  subscriptions: SubscriptionsPersistence,
  users: UserPersistence,
  producer: SubscriptionProducer,
): SubscriptionService = Subscriptions(subscriptions, users, producer)

private class Subscriptions(
  private val subscriptions: SubscriptionsPersistence,
  private val users: UserPersistence,
  private val producer: SubscriptionProducer,
) : SubscriptionService {
  override suspend fun findAll(slackUserId: SlackUserId): List<Subscription> =
    users.findSlackUser(slackUserId)
      ?.let { user -> subscriptions.findAll(user.userId) }.orEmpty()
  
  override suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription) {
    val user = users.insertSlackUser(slackUserId)
    subscriptions.subscribe(user.userId, subscription)
    producer.publish(subscription.repository)
  }
  
  override suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository) {
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
