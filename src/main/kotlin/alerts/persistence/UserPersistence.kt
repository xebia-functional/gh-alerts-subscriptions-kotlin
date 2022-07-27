package alerts.persistence

import alerts.sqldelight.UsersQueries
import arrow.core.NonEmptyList
import io.prometheus.client.Counter
import kotlinx.serialization.Serializable

@JvmInline
value class UserId(val serial: Long)

@JvmInline
@Serializable
value class SlackUserId(val slackUserId: String)

data class User(val userId: UserId, val slackUserId: SlackUserId)

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): User
}

fun userPersistence(queries: UsersQueries, slackUsersCounter: Counter): UserPersistence = object : UserPersistence {
  override suspend fun find(userId: UserId): User? =
    queries.find(userId, ::User).executeAsOneOrNull()
  
  override suspend fun findSlackUser(slackUserId: SlackUserId): User? =
    queries.findSlackUser(slackUserId, ::User).executeAsOneOrNull()
  
  override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> =
    queries.findUsers(userIds, ::User).executeAsList()
  
  override suspend fun insertSlackUser(slackUserId: SlackUserId): User =
    queries.transactionWithResult {
      queries.findSlackUser(slackUserId, ::User).executeAsOneOrNull()
        ?: User(queries.insert(slackUserId).executeAsOne(), slackUserId)
          .also { slackUsersCounter.inc() }
    }
}
