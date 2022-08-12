package alerts.service

import alerts.https.client.GithubClient
import alerts.https.client.GithubError
import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserId
import alerts.persistence.UserPersistence
import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.ktor.http.HttpStatusCode

typealias MissingRepo = EffectScope<RepoNotFound>
data class RepoNotFound(val repository: Repository)

typealias MissingSlackUser = EffectScope<SlackUserNotFound>
data class SlackUserNotFound(val slackUserId: SlackUserId)

interface SubscriptionService {
  /** Returns all subscriptions for the given [slackUserId], empty if none found */
  context(MissingSlackUser)
  suspend fun findAll(slackUserId: SlackUserId): List<Subscription>
  
  /**
   * Subscribes to the provided [Subscription], only if the [Repository] exists.
   * If this is a **new** subscription for the user a [SubscriptionEvent.Created] event is sent.
   */
  context(MissingRepo, MissingSlackUser)
  suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription)
  
  /**
   * Unsubscribes the repo. No-op if the [slackUserId] was not subscribed to the repo.
   * If the [Repository] has no more subscriptions a [SubscriptionEvent.Deleted] event is sent.
   */
  context(MissingSlackUser)
  suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository)
}

fun SubscriptionService(
  subscriptions: SubscriptionsPersistence,
  users: UserPersistence,
  producer: SubscriptionProducer,
  client: GithubClient,
): SubscriptionService = Subscriptions(subscriptions, users, producer, client)

private class Subscriptions(
  private val subscriptions: SubscriptionsPersistence,
  private val users: UserPersistence,
  private val producer: SubscriptionProducer,
  private val client: GithubClient,
) : SubscriptionService {
  context(MissingSlackUser)
  override suspend fun findAll(slackUserId: SlackUserId): List<Subscription> {
    val user = users.findSlackUser(slackUserId)
    ensureNotNull(user) { SlackUserNotFound(slackUserId) }
    return subscriptions.findAll(user)
  }
  
  context(EffectScope<GithubError>, MissingRepo)
  override suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription) {
    val user = users.insertSlackUser(slackUserId)
    val exists = client.repositoryExists(subscription.repository.owner, subscription.repository.name).bind()
    ensure(exists) { RepoNotFound(subscription.repository) }
    val hasSubscribers = subscriptions.findSubscribers(subscription.repository).isNotEmpty()
    subscriptions.subscribe(user, subscription)
    return if (!hasSubscribers) producer.publish(subscription.repository) else Unit
  }
  
  context(MissingSlackUser)
  override suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository) {
    val user = ensureNotNull(users.findSlackUser(slackUserId)) { SlackUserNotFound(slackUserId) }
    subscriptions.unsubscribe(user, repository)
    val subscribers = subscriptions.findSubscribers(repository)
    return if (subscribers.isEmpty()) producer.delete(repository) else Unit
  }
}
