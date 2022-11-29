package alerts.github

import alerts.HttpStatusCodeSerializer
import alerts.user.SlackUserId
import arrow.core.continuations.EffectScope
import com.github.avrokotlin.avro4k.AvroNamespace
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

typealias GithubErrors = EffectScope<GithubError>

@JvmInline
value class GithubError(private val statusCode: HttpStatusCode) {
  fun asJson(): String = Json.encodeToString(HttpStatusCodeSerializer, statusCode)
}

@Serializable
@AvroNamespace("alerts.domain")
data class GithubEvent(val event: String)

@Serializable
@AvroNamespace("alerts.domain")
data class SlackNotification(val userId: SlackUserId, val event: String)
