package alerts.service

import alerts.https.client.GithubClient
import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserId
import alerts.persistence.UserPersistence
import arrow.core.Either
import arrow.core.continuations.either
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import kotlin.math.log

interface SubscriptionService {
  /** Returns all subscriptions for the given [slackUserId], empty if none found */
  suspend fun findAll(slackUserId: SlackUserId): List<Subscription>
  
  /**
   * Subscribes to the provided [Subscription], only if the [Repository] exists.
   * If this is a **new** subscription for the user a [SubscriptionEvent.Created] event is send.
   */
  suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription): Either<SubscriptionError, Unit>
  
  /**
   * Unsubscribes the repo. No-op if the [slackUserId] was not subscribed to the repo.
   * If the [Repository] has no more subscriptions a [SubscriptionEvent.Deleted] event is send.
   */
  suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository)
}

sealed interface SubscriptionError
data class RepoNotFound(val repository: Repository, val statusCode: HttpStatusCode? = null) : SubscriptionError
data class UserNotFound(val user: UserId, val slackUserId: SlackUserId) : SubscriptionError

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
  private val logger = KotlinLogging.logger {  }
  
  override suspend fun findAll(slackUserId: SlackUserId): List<Subscription> =
    users.findSlackUser(slackUserId)
      ?.let { user -> subscriptions.findAll(user.userId) }.orEmpty()
  
  override suspend fun subscribe(
    slackUserId: SlackUserId,
    subscription: Subscription,
  ): Either<SubscriptionError, Unit> =
    either {
      val user = users.insertSlackUser(slackUserId)
      
      val exists = client.repositoryExists(subscription.repository.owner, subscription.repository.name)
        .mapLeft { RepoNotFound(subscription.repository, it.statusCode) }.bind()
      
      ensure(exists) { RepoNotFound(subscription.repository) }
      
      val hasSubscribers = subscriptions.findSubscribers(subscription.repository).isNotEmpty()
      
      subscriptions.subscribe(user.userId, subscription)
        .mapLeft { UserNotFound(it.userId, slackUserId) }.bind()
      
      logger.info { "hasSubscribers: $hasSubscribers => " }
      if (!hasSubscribers) {
        producer.publish(subscription.repository)
      }
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
