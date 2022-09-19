package alerts.user

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class UserId(val serial: Long)

@JvmInline
@Serializable
value class SlackUserId(val slackUserId: String)

data class User(val userId: UserId, val slackUserId: SlackUserId)
