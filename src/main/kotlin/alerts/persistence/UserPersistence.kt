package alerts.persistence

import alerts.sqldelight.UsersQueries
import arrow.core.NonEmptyList

@JvmInline
value class UserId(val serial: Long)

@JvmInline
value class SlackUserId(val slackUserId: String)

data class User(val userId: UserId, val slackUserId: SlackUserId)

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): User
}

fun userPersistence(queries: UsersQueries) = object : UserPersistence {
  override suspend fun find(userId: UserId): User? =
    queries.find(userId, ::User).executeAsOneOrNull()

  override suspend fun findSlackUser(slackUserId: SlackUserId): User? =
    queries.findSlackUser(slackUserId, ::User).executeAsOneOrNull()

  override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> =
    queries.findUsers(userIds, ::User).executeAsList()

  override suspend fun insertSlackUser(slackUserId: SlackUserId): User =
    User(queries.insert(slackUserId).executeAsOne(), slackUserId)
}
