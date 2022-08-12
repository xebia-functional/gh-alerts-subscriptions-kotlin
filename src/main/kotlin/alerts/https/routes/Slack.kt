package alerts.https.routes

import alerts.StatusCodeError
import alerts.Time
import alerts.or
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.respond
import alerts.service.SubscriptionService
import alerts.statusCode
import arrow.core.continuations.ensureNotNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
enum class Command { Subscribe }

@Serializable
data class SlashCommand(
  val userId: SlackUserId,
  val command: Command,
  val repo: Repository,
)

fun Routing.slackRoutes(service: SubscriptionService, time: Time = Time.UTC): Route =
  get("/slack/command") {
    respond(Created) {
      val command = call.receiveParameters().decodeSlashCommand()
      ensure(command.command == Command.Subscribe) { statusCode(InternalServerError) }
      service.subscribe(command.userId, Subscription(command.repo, time.now()))
    }
  }

context(StatusCodeError)
private suspend fun Parameters.decodeSlashCommand(): SlashCommand {
  val command = ensureNotNull(get("command")) { badRequest("no command specified") }
  ensure(command == "/subscribe") { badRequest("unknown command: $command") }
  val parts = ensureNotNull(get("text")?.split("/")) { badRequest("missing owner/repository") }
  ensure(parts.size == 2) { badRequest("missing owner/repository") }
  val repo = Repository(parts[0], parts[1])
  val slackUserId = ensureNotNull(get("user_id")?.let(::SlackUserId)) { badRequest("missing user_id") }
  return SlashCommand(slackUserId, Command.Subscribe, repo)
}

private fun badRequest(msg: String) =
  TextContent(msg, ContentType.Text.Plain, BadRequest)
