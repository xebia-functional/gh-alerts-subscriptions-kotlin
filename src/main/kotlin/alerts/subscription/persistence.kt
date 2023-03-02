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
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.time.LocalDateTime

@Table(value = "subscriptions")
data class SubscriptionDTO(
    val owner: String,
    val repository: String,
    @Column("subscribed_at")
    val subscribedAt: LocalDateTime
)

@Table(value = "repositories")
data class RepositoryDTO(
    @Id
    @Column("repository_id")
    val repositoryId: Long,
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
        WHERE subscriptions.user_id = :userId
    """)
    fun findAllByUserId(userId: Long): Flow<SubscriptionDTO>

    @Query("""
        SELECT subscriptions.user_id
        FROM subscriptions
        INNER JOIN repositories ON subscriptions.repository_id = repositories.repository_id
        WHERE repositories.owner = :repositoryOwner
        AND   repositories.repository = :repositoryName
    """)
    fun findAllSubscribers(repositoryOwner: String, repositoryName: String): Flow<UserId>

    @Query("""
        INSERT INTO subscriptions (user_id, repository_id, subscribed_at)
        VALUES (:userId, :repositoryId, :subscribedAt)
        ON CONFLICT DO NOTHING
    """)
    suspend fun insertSubscription(userId: Long, repositoryId: Long, subscribedAt: LocalDateTime): Unit

    @Query("""
        DELETE FROM subscriptions
        WHERE user_id = :userId AND repository_id IN (
        SELECT repository_id FROM repositories
        WHERE owner = :owner AND repository = :repository
        )
    """)
    suspend fun deleteSubscription(userId: Long, owner: String, repository: String): Unit

}

interface RepositoryRepo : CoroutineCrudRepository<RepositoryDTO, Long> {
    @Query("""
        INSERT INTO repositories(owner, repository)
        VALUES (:repositoryOwner, :repositoryName)
        ON CONFLICT DO NOTHING
        RETURNING repository_id
    """)
    suspend fun insert(repositoryOwner: String, repositoryName: String): Long
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
    private val repositories: RepositoryRepo,
    private val transactionOperator: TransactionalOperator
) : SubscriptionsPersistence {
    override suspend fun findAll(user: UserId): List<Subscription> =
        subscriptions.findAllByUserId(user.serial).map { subscription ->
            Subscription(
                Repository(subscription.owner, subscription.repository),
                subscription.subscribedAt.toKotlinLocalDateTime()
            )
        }.toList()

    override suspend fun findSubscribers(repository: Repository): List<UserId> =
        subscriptions.findAllSubscribers(repository.owner, repository.name).toList()

    override suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit> =
        transactionOperator.executeAndAwait {
            subscription.traverse { (repository, subscribedAt) ->
                val repoId = repositories.insert(repository.owner, repository.name)

                catch({
                    subscriptions.insertSubscription(user.serial, repoId, subscribedAt.toJavaLocalDateTime())
                }) { error: PSQLException ->
                    if (error.isUserIdForeignKeyViolation()) UserNotFound(user)
                    else throw error
                }
            }.fold({ it.left() }, { Unit.right() })
        } ?: error("Failed to subscribe")

    override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) {
        if (repositories.isEmpty()) Unit else transactionOperator.executeAndAwait {
            repositories.forEach { (owner, name) ->
                subscriptions.deleteSubscription(user.serial, owner, name)
            }
        }
    }

    fun PSQLException.isUserIdForeignKeyViolation(): Boolean =
        sqlState == PSQLState.FOREIGN_KEY_VIOLATION.state &&
                message?.contains("subscriptions_user_id_fkey") == true
}
