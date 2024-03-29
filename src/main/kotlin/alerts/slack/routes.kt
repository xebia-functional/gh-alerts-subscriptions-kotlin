package alerts.slack

import alerts.badRequest
import alerts.env.Routes
import alerts.subscription.Repository
import alerts.user.SlackUserId
import alerts.subscription.Subscription
import alerts.respond
import alerts.subscription.SubscriptionService
import alerts.statusCode
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Routing.slackRoutes(service: SubscriptionService) =
  get<Routes.Slack> {
    either {
      val command = call.receiveParameters().decodeSlashCommand().bind()
      ensure(command.command == Command.Subscribe) { statusCode(InternalServerError) }
      service.subscribe(command.userId, Subscription(command.repo, Clock.System.now().toLocalDateTime(TimeZone.UTC)))
        .mapLeft { statusCode(BadRequest) }.bind()
    }.respond(Created)
  }

private suspend fun Parameters.decodeSlashCommand(): Either<TextContent, SlashCommand> =
  either {
    val command = ensureNotNull(get("command")) { badRequest("no command specified") }
    ensure(command == "/subscribe") { badRequest("unknown command: $command") }
    val parts = ensureNotNull(get("text")?.split("/")) { badRequest("missing owner/repository") }
    ensure(parts.size == 2) { badRequest("missing owner/repository") }
    val repo = Repository(parts[0], parts[1])
    val slackUserId = ensureNotNull(get("user_id")?.let(::SlackUserId)) { badRequest("missing user_id") }
    SlashCommand(slackUserId, Command.Subscribe, repo)
  }
