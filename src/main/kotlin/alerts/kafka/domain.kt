package alerts.domain

import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import com.github.avrokotlin.avro4k.AvroNamespace
import kotlinx.serialization.Serializable


@Serializable
@AvroNamespace("alerts.domain.subscription")
data class SubscriptionKey(val repository: Repository)

@Serializable
@AvroNamespace("alerts.domain.subscription")
enum class SubscriptionEvent {
  Created, Deleted;
}

@Serializable
data class GithubEvent(val event: String)

@Serializable
data class SlackNotification(val userId: SlackUserId, val event: String)
