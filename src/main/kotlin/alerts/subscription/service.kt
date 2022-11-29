package alerts.subscription

import arrow.core.continuations.EffectScope
import alerts.github.GithubClient
import alerts.github.GithubErrors
import alerts.user.SlackUserId
import alerts.user.UserPersistence
import arrow.core.continuations.ensureNotNull
import mu.KotlinLogging

typealias MissingRepo = EffectScope<RepoNotFound>

interface SubscriptionService {
  /** Returns all subscriptions for the given [slackUserId], empty if none found */
  context(MissingSlackUser)
  suspend fun findAll(slackUserId: SlackUserId): List<Subscription>
  
  /**
   * Subscribes to the provided [Subscription], only if the [Repository] exists.
   * If this is a **new** subscription for the user a [SubscriptionEvent.Created] event is sent.
   */
  context(MissingRepo, GithubErrors)
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
): SubscriptionService = SqlDelightSubscriptionService(subscriptions, users, producer, client)

private class SqlDelightSubscriptionService(
  private val subscriptions: SubscriptionsPersistence,
  private val users: UserPersistence,
  private val producer: SubscriptionProducer,
  private val client: GithubClient,
) : SubscriptionService {
  private val logger = KotlinLogging.logger { }
  
  context(MissingSlackUser)
  override suspend fun findAll(slackUserId: SlackUserId): List<Subscription> {
    val user = users.findSlackUser(slackUserId)
    ensureNotNull(user) { SlackUserNotFound(slackUserId) }
    return subscriptions.findAll(user)
  }
  
  context(GithubErrors, MissingRepo)
  override suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription) {
    val user = users.insertSlackUser(slackUserId)
    val exists = client.repositoryExists(subscription.repository.owner, subscription.repository.name).bind()
    ensure(exists) { RepoNotFound(subscription.repository) }
    val hasSubscribers = subscriptions.findSubscribers(subscription.repository).isNotEmpty()
    subscriptions.subscribe(user, subscription)
    logger.info { "hasSubscribers: $hasSubscribers => " }
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
