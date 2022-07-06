package alerts.programs

import alerts.kafka.GithubEvent
import alerts.kafka.GithubEventProcessor
import alerts.kafka.SlackNotification
import alerts.persistence.Repository
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserPersistence
import alerts.persistence.catch
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import arrow.optics.Optional
import io.github.nomisrev.JsonPath
import io.github.nomisrev.path
import io.github.nomisrev.string
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mu.KLogger

private sealed interface NotificationError
private data class MalformedJson(
  val json: String,
  val exception: SerializationException,
) : NotificationError

private data class RepoFullNameNotFound(val json: JsonElement) : NotificationError
private data class CannotExtractRepo(val fullName: String) : NotificationError

class Notifications(
  private val users: UserPersistence,
  private val service: SubscriptionsPersistence,
  private val processor: GithubEventProcessor,
  private val logger: KLogger,
) {
  private val fullNamePath: Optional<JsonElement, String> = JsonPath.path("repository.full_name.string").string
  
  suspend fun process(): Unit =
    processor.process { event ->
      findSubscribers(event).fold({ error ->
        // We currently simply log errors for failures
        error.log()
        emptyFlow()
      }, List<SlackNotification>::asFlow)
    }.collect()
  
  private suspend fun extractRepo(event: GithubEvent): Either<NotificationError, Repository> =
    either {
      val json = catch({ Json.parseToJsonElement(event.event) }) { error: SerializationException ->
        MalformedJson(event.event, error)
      }.bind()
      val fullName = ensureNotNull(fullNamePath.getOrNull(json)) { RepoFullNameNotFound(json) }
      val (owner, name) = ensureNotNull(fullName.split("/").takeIf { it.size == 2 }) { CannotExtractRepo(fullName) }
      Repository(owner, name)
    }
  
  private suspend fun findSubscribers(event: GithubEvent): Either<NotificationError, List<SlackNotification>> = either {
    val repo = extractRepo(event).bind()
    val userIds = service.findSubscribers(repo)
    val slackUserIds = userIds.mapNotNull { users.find(it)?.slackUserId }
    slackUserIds.map { SlackNotification(it, event.event) }
  }
  
  private fun NotificationError.log(): Unit =
    when (this) {
      is RepoFullNameNotFound -> logger.info { "Didn't find `repository.full_name` in JSON. $json." }
      is MalformedJson -> logger.info(exception) { "Received malformed JSON from GithubEvent" }
      is CannotExtractRepo ->
        logger.info { "full_name received in unexpected format. Expected `owner/repo` but found $fullName" }
    }
}
