package alerts.user

import arrow.core.NonEmptyList
import io.prometheus.client.Counter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Table(name = "users")
data class UserDTO(
  @Id
  @Column("user_id")
  val userId: Long,
  @Column("slack_user_id")
  val slackUserId: String
)

interface UserRepo : CoroutineCrudRepository<UserDTO, Long> {

  suspend fun findBySlackUserId(slackUserId: String): UserDTO?

  @Query("INSERT INTO users(slack_user_id) VALUES (:slackUserId) RETURNING user_id")
  suspend fun insertSlackUserId(slackUserId: String): Long
}

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): User
}

@Component
class DefaultUserPersistence(
  private val queries: UserRepo,
  private val slackUsersCounter: Counter,
  private val transactionOperator: TransactionalOperator
) : UserPersistence {
  override suspend fun find(userId: UserId): User? =
    queries.findById(userId.serial)
      ?.let { User(UserId(it.userId), SlackUserId(it.slackUserId)) }

  override suspend fun findSlackUser(slackUserId: SlackUserId): User? =
    queries.findBySlackUserId(slackUserId.slackUserId)
      ?.let { User(UserId(it.userId), SlackUserId(it.slackUserId)) }

  override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> =
    queries.findAllById(userIds.map { it.serial })
      .map { User(UserId(it.userId), SlackUserId(it.slackUserId)) }
      .toList()

  override suspend fun insertSlackUser(slackUserId: SlackUserId): User =
    transactionOperator.executeAndAwait {
      (queries.findBySlackUserId(slackUserId.slackUserId) ?:
        UserDTO(queries.insertSlackUserId(slackUserId.slackUserId), slackUserId.slackUserId)
          .also { slackUsersCounter.inc() }
      ).let { User(UserId(it.userId), SlackUserId(it.slackUserId)) }
    }
}
