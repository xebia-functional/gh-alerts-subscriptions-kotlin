package alerts.slack

import alerts.subscription.Repository
import alerts.subscription.Subscription
import alerts.subscription.SubscriptionService
import alerts.user.SlackUserId
import arrow.core.Either
import arrow.core.identity
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class SlackController(private val service: SubscriptionService) {
  @GetMapping(
    "/slack/command",
    consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
  )
  suspend fun get(
    @RequestParam params: MultiValueMap<String, String>
  ): ResponseEntity<String> =
    either<ResponseEntity<String>, Unit> {
      val command = params.decodeSlashCommand().bind()
      ensure(command.command == Command.Subscribe) { ResponseEntity(INTERNAL_SERVER_ERROR) }

      catch({
        service.subscribe(command.userId, Subscription(command.repo, Clock.System.now().toLocalDateTime(TimeZone.UTC)))
      }) {
        raise(ResponseEntity(BAD_REQUEST))
      }

    }.fold(::identity) { ResponseEntity(HttpStatus.CREATED) }
}

private suspend fun MultiValueMap<String, String>.decodeSlashCommand(): Either<ResponseEntity<String>, SlashCommand> =
  either {
    val command = ensureNotNull(getFirst("command")) { badRequest("no command specified") }
    ensure(command == "/subscribe") { badRequest("unknown command: $command") }
    val parts = ensureNotNull(getFirst("text")?.split("/")) { badRequest("missing owner/repository") }
    ensure(parts.size == 2) { badRequest("missing owner/repository") }
    val repo = Repository(parts[0], parts[1])
    val slackUserId = ensureNotNull(getFirst("user_id")?.let(::SlackUserId)) { badRequest("missing user_id") }
    SlashCommand(slackUserId, Command.Subscribe, repo)
  }

private fun badRequest(content: String): ResponseEntity<String> =
  ResponseEntity.status(BAD_REQUEST).body(content)
