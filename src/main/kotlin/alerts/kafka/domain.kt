package alerts.domain

import alerts.persistence.SlackUserId
import kotlinx.serialization.Serializable


@Serializable
data class GithubEvent(val event: String)

@Serializable
data class SlackNotification(val userId: SlackUserId, val event: String)
