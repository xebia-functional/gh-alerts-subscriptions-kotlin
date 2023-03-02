package alerts.subscription

import alerts.catch
import alerts.user.UserId
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.traverse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Table(value = "subscriptions")
data class SubscriptionDTO(
    @Id val id: Long,
    val owner: String,
    val repository: Repository,
    val subscribedAt: LocalDateTime
)

@Table(value = "repositories")
data class RepositoryDTO(
  @Id val id: Long,
  val owner: String,
  val repository: String
)

interface SubscriptionRepo : CoroutineCrudRepository<SubscriptionDTO, Long> {
    @Query("""
      SELECT repositories.owner,
             repositories.repository,
             subscriptions.subscribed_at
      FROM repositories
      INNER JOIN subscriptions ON subscriptions.repository_id = repositories.repository_id
      WHERE subscriptions.user_id = :userId;
    """)
    fun findAllByUserId(userId: Long): Flow<SubscriptionDTO>

    @Query("""
      SELECT subscriptions.user_id
      FROM subscriptions
      INNER JOIN repositories ON subscriptions.repository_id = repositories.repository_id
      WHERE repositories.owner = :repositoryOwner
      AND   repositories.repository = :repositoryName;
    """)
    fun findAllSubscribers(repositoryOwner: String, repositoryName: String): Flow<UserId>

  @Query("""
    INSERT INTO subscriptions (user_id, repository_id, subscribed_at)
    VALUES (:userId, :repositoryId, :subscribedAt)
    ON CONFLICT DO NOTHING;
    """)
    suspend fun insert(userId: Long, repositoryId: Long, subscribedAt: LocalDateTime)
}

interface RepositoryRepo : CoroutineCrudRepository<RepositoryDTO, Long> {
  @Query("""
    INSERT INTO repositories(owner, repository)
    VALUES (:repositoryOwner, :repositoryName)
    ON CONFLICT DO NOTHING
    RETURNING repository_id;
  """)
  suspend fun insert(repositoryOwner: String, repositoryName: String): RepositoryId
}

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

@Component
class DefaultSubscriptionsPersistence(
    private val subscriptions: SubscriptionRepo,
    private val repositories: RepositoryRepo
) : SubscriptionsPersistence {
    override suspend fun findAll(user: UserId): List<Subscription> =
      subscriptions.findAllByUserId(user.serial).map { subscription ->
            Subscription(subscription.repository, subscription.subscribedAt)
        }.toList()

    override suspend fun findSubscribers(repository: Repository): List<UserId> =
      subscriptions.findAllSubscribers(repository.owner, repository.name).toList()

    @Transactional
    override suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit> =
      subscription.traverse { (repository, subscribedAt) ->
        val repoId = repositories.insert(repository.owner, repository.name)

        catch({
          subscriptions.insert(user.serial, repoId.serial, subscribedAt)
        }) { error: PSQLException ->
          if (error.isUserIdForeignKeyViolation()) UserNotFound(user)
          else throw error
        }
      }.fold({ it.left() }, { Unit.right() })


    @Transactional
    override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) {
        TODO("Not yet implemented")
    }

    fun PSQLException.isUserIdForeignKeyViolation(): Boolean =
      sqlState == PSQLState.FOREIGN_KEY_VIOLATION.state && message?.contains("subscriptions_user_id_fkey") == true

}
