package alerts.persistence

import alerts.sqldelight.RepositoriesQueries
import alerts.sqldelight.SubscriptionsQueries
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.traverse
import com.github.avrokotlin.avro4k.AvroNamespace
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import java.time.LocalDateTime

@JvmInline
value class RepositoryId(val serial: Long)

@Serializable
@AvroNamespace("alerts.domain.repository")
data class Repository(val owner: String, val name: String)

data class Subscription(val repository: Repository, val subscribedAt: LocalDateTime)

data class UserNotFound(val userId: UserId)

interface SubscriptionsPersistence {
  suspend fun findAll(user: UserId): List<Subscription>
  suspend fun findSubscribers(repository: Repository): List<UserId>
  suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit>
  suspend fun unsubscribe(user: UserId, repositories: List<Repository>): Unit
  
  suspend fun subscribe(user: UserId, subscription: Subscription): Either<UserNotFound, Unit> =
    subscribe(user, listOf(subscription))
  
  suspend fun unsubscribe(user: UserId, repositories: Repository): Unit =
    unsubscribe(user, listOf(repositories))
}

fun subscriptionsPersistence(
  subscriptions: SubscriptionsQueries,
  repositories: RepositoriesQueries,
): SubscriptionsPersistence = object : SubscriptionsPersistence {
  override suspend fun findAll(user: UserId): List<Subscription> =
    subscriptions.findAll(user) { owner, repository, subscribedAt ->
      Subscription(Repository(owner, repository), subscribedAt)
    }.executeAsList()
  
  override suspend fun findSubscribers(repository: Repository): List<UserId> =
    subscriptions.findSubscribers(repository.owner, repository.name).executeAsList()
  
  override suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit> =
    subscriptions.transactionWithResult {
      subscription.traverse { (repository, subscribedAt) ->
        val repoId =
          repositories.insert(repository.owner, repository.name).executeAsOneOrNull() ?: repositories.selectId(
            repository.owner, repository.name
          ).executeAsOne()
        
        catch({
          subscriptions.insert(user, repoId, subscribedAt)
        }) { error: PSQLException -> if (error.isUserIdForeignKeyViolation()) UserNotFound(user) else throw error }
      }.fold({ rollback(it.left()) }, { Unit.right() })
    }
  
  private fun PSQLException.isUserIdForeignKeyViolation(): Boolean =
    isForeignKeyViolation() && message?.contains("subscriptions_user_id_fkey") == true
  
  override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) =
    if (repositories.isEmpty()) Unit else subscriptions.transaction {
      repositories.forEach { (owner, name) ->
        subscriptions.delete(user, owner, name)
      }
    }
}
