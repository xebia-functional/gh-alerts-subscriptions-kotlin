package alerts.persistence

import alerts.sqldelight.RepositoriesQueries
import alerts.sqldelight.SubscriptionsQueries
import alerts.sqldelight.UsersQueries
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.traverse
import com.github.avrokotlin.avro4k.AvroNamespace
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import kotlin.coroutines.Continuation

@JvmInline
value class RepositoryId(val serial: Long)

@Serializable
@AvroNamespace("alerts.domain.repository")
data class Repository(val owner: String, val name: String)

@Serializable
data class Subscription(val repository: Repository, val subscribedAt: LocalDateTime)

data class UserNotFound(val userId: UserId)

interface SubscriptionsPersistence {
  suspend fun findAll(user: User): List<Subscription>
  suspend fun findSubscribers(repository: Repository): List<UserId>
  suspend fun subscribe(user: User, subscription: List<Subscription>): Unit
  suspend fun unsubscribe(user: User, repositories: List<Repository>): Unit
  
  suspend fun subscribe(user: User, subscription: Subscription): Unit =
    subscribe(user, listOf(subscription))
  
  suspend fun unsubscribe(user: User, repositories: Repository): Unit =
    unsubscribe(user, listOf(repositories))
}

fun SubscriptionsPersistence(
  subscriptions: SubscriptionsQueries,
  repositories: RepositoriesQueries
): SubscriptionsPersistence = object : SubscriptionsPersistence {
  override suspend fun findAll(user: User): List<Subscription> =
    subscriptions.findAll(user.userId) { owner, repository, subscribedAt ->
      Subscription(Repository(owner, repository), subscribedAt.toKotlinLocalDateTime())
    }.executeAsList()
  
  override suspend fun findSubscribers(repository: Repository): List<UserId> =
    subscriptions.findSubscribers(repository.owner, repository.name).executeAsList()
  
  override suspend fun subscribe(user: User, subscription: List<Subscription>): Unit =
    subscriptions.transactionWithResult {
      subscription.forEach { (repository, subscribedAt) ->
        val repoId =
          repositories.insert(repository.owner, repository.name).executeAsOneOrNull() ?: repositories.selectId(
            repository.owner, repository.name
          ).executeAsOne()
    
        subscriptions.insert(user.userId, repoId, subscribedAt.toJavaLocalDateTime())
      }
    }
  
  override suspend fun unsubscribe(user: User, repositories: List<Repository>): Unit =
    if (repositories.isEmpty()) Unit else subscriptions.transaction {
      repositories.forEach { (owner, name) ->
        subscriptions.delete(user.userId, owner, name)
      }
    }
}
