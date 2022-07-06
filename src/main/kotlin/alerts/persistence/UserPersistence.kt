package alerts.persistence

import alerts.sqldelight.UsersQueries
import arrow.core.Either
import arrow.core.NonEmptyList
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline
value class UserId(val serial: Long)

@JvmInline
@Serializable
value class SlackUserId(val slackUserId: String)

data class User(val userId: UserId, val slackUserId: SlackUserId)

data class UserAlreadyExists(val message: SlackUserId)

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): Either<UserAlreadyExists, User>
  suspend fun findOrInsertSlackUser(slackUserId: SlackUserId): User =
    findSlackUser(slackUserId) ?: requireNotNull(insertSlackUser(slackUserId).orNull())
}

fun userPersistence(queries: UsersQueries): UserPersistence = object : UserPersistence {
  override suspend fun find(userId: UserId): User? =
    queries.find(userId, ::User).executeAsOneOrNull()

  override suspend fun findSlackUser(slackUserId: SlackUserId): User? =
    queries.findSlackUser(slackUserId, ::User).executeAsOneOrNull()

  override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> =
    queries.findUsers(userIds, ::User).executeAsList()

  override suspend fun insertSlackUser(slackUserId: SlackUserId): Either<UserAlreadyExists, User> =
    catch({
      User(queries.insert(slackUserId).executeAsOne(), slackUserId)
    }) { error: PSQLException ->
      if (error.isUniqueViolation()) UserAlreadyExists(slackUserId) else throw error
    }
}
