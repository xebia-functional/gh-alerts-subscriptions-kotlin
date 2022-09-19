package alerts.github

import alerts.user.SlackUserId
import com.github.avrokotlin.avro4k.AvroNamespace
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@JvmInline
value class GithubError(val statusCode: HttpStatusCode)

@Serializable
@AvroNamespace("alerts.domain")
data class GithubEvent(val event: String)

@Serializable
@AvroNamespace("alerts.domain")
data class SlackNotification(val userId: SlackUserId, val event: String)
