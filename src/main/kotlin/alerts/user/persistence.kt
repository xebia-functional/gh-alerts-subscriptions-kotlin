package alerts.user

import alerts.sqldelight.UsersQueries
import arrow.core.NonEmptyList
import io.prometheus.client.Counter

interface UserPersistence {
  suspend fun find(userId: UserId): User?
  suspend fun findSlackUser(slackUserId: SlackUserId): User?
  suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User>
  suspend fun insertSlackUser(slackUserId: SlackUserId): User
}

class SqlDelightUserPersistence(private val queries: UsersQueries, private val slackUsersCounter: Counter) :
  UserPersistence {
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
