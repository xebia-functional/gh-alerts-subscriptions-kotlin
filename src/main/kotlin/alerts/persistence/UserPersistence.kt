package alerts.persistence

import alerts.sqldelight.UsersQueries
import arrow.core.Either
import arrow.core.NonEmptyList
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline
value class UserId(val serial: Long)

@JvmInline
value class SlackUserId(val slackUserId: String)

data class User(val userId: UserId, val slackUserId: SlackUserId)

data class UserAlreadyExists(val message: SlackUserId)

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): Either<UserAlreadyExists, User>
}

fun userPersistence(queries: UsersQueries) = object : UserPersistence {
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
      if (error.sqlState == PSQLState.UNIQUE_VIOLATION.state) UserAlreadyExists(slackUserId)
      else null
    }
}
