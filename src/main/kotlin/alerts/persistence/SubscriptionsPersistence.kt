package alerts.persistence

import alerts.sqldelight.RepositoriesQueries
import alerts.sqldelight.SubscriptionsQueries
import java.time.LocalDateTime

@JvmInline
value class RepositoryId(val serial: Long)

data class Repository(val owner: String, val name: String)

data class Subscription(val repository: Repository, val subscribedAt: LocalDateTime)

interface SubscriptionsPersistence {
  suspend fun findAll(user: UserId): List<Subscription>
  suspend fun findSubscribers(repository: Repository): List<UserId>
  suspend fun subscribe(user: UserId, subscription: List<Subscription>): Unit
  suspend fun unsubscribe(user: UserId, repositories: List<Repository>): Unit
}

fun subscriptionsPersistence(
  subscriptions: SubscriptionsQueries,
  repositories: RepositoriesQueries
) = object : SubscriptionsPersistence {
  override suspend fun findAll(user: UserId): List<Subscription> =
    subscriptions.findAll(user) { owner, repository, subscribedAt ->
      Subscription(Repository(owner, repository), subscribedAt)
    }.executeAsList()

  override suspend fun findSubscribers(repository: Repository): List<UserId> =
    subscriptions.findSubscribers(repository.owner, repository.name).executeAsList()

  override suspend fun subscribe(user: UserId, subscription: List<Subscription>) =
    subscriptions.transaction {
      subscription.forEach { (repository, subscribedAt) ->
        val repoId = repositories.insert(repository.owner, repository.name).executeAsOne()
        subscriptions.insert(user, repoId, subscribedAt)
      }
    }

  override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) =
    subscriptions.transaction {
      repositories.forEach { (owner, name) ->
        subscriptions.delete(user, owner, name)
      }
    }
}
