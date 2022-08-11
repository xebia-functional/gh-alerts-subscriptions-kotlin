package alerts.https.routes

import alerts.badRequest
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.respond
import alerts.service.RepoNotFound
import alerts.service.SubscriptionError
import alerts.service.SubscriptionService
import alerts.statusCode
import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import com.github.avrokotlin.avro4k.schema.schemaFor
import guru.zoroark.koa.ktor.describe
import guru.zoroark.koa.dsl.schema
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  clock: Clock = Clock.System,
  timeZone: TimeZone = TimeZone.UTC,
) =
  route("subscription") {
    get {
      either {
        val slackUserId = call.slackUserId()
        val subscriptions = service.findAll(slackUserId).mapLeft { statusCode(BadRequest) }.bind()
        Subscriptions(subscriptions)
      }.respond()
    } describe {
      OK.value response ContentType.Application.Json.contentType { schema(subscriptionsExample) }
      BadRequest.value response { }
    }
    
    post {
      either {
        val slackUserId = call.slackUserId()
        val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { statusCode(BadRequest) }
        service.subscribe(slackUserId, Subscription(repository, clock.now().toLocalDateTime(timeZone)))
          .mapLeft { badRequest(it.toJson(), ContentType.Application.Json) }.bind()
      }.respond(Created)
    } describe {
      OK.value response { }
      BadRequest.value response ContentType.Application.Json.contentType {
        schema<SubscriptionError>(RepoNotFound(Repository("non-existing-owner", "repo")))
      }
    }
    
    delete {
      either {
        val slackUserId = call.slackUserId()
        val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { statusCode(BadRequest) }
        service.unsubscribe(slackUserId, repository).mapLeft { statusCode(NotFound) }.bind()
      }.respond(NoContent)
    } describe {
      NoContent.value response {
        description = "Deleted the subscription for the given user."
      }
      BadRequest.value response {
        description = "Did not receive a correct slackUserId or repository."
      }
      NotFound.value response {
        description = "No subscription to delete was found for the given user, or repository."
      }
    }
  }

private val subscriptionsExample =
  Subscriptions(listOf(Subscription(Repository("arrow-kt", "arrow"), Clock.System.now().toLocalDateTime(TimeZone.UTC))))

private fun SubscriptionError.toJson(): String =
  Json.encodeToString(SubscriptionError.serializer(), this)

context(EffectScope<OutgoingContent>)
  private suspend fun ApplicationCall.slackUserId(): SlackUserId =
  SlackUserId(ensureNotNull(request.queryParameters["slackUserId"]) { statusCode(BadRequest) })
