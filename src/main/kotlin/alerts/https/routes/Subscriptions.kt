package alerts.https.routes

import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.service.SubscriptionService
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  clock: Clock = Clock.System,
  timeZone: TimeZone = TimeZone.UTC,
) =
  route("subscription/{slackUserId}") { // TODO change this to query param?
    get {
      either {
        val slackUserId = SlackUserId(ensureNotNull(call.parameters["slackUserId"]) { BadRequest })
        val subscriptions = service.findAll(slackUserId).mapLeft { BadRequest }.bind()
        Subscriptions(subscriptions)
      }.respond()
    }
    
    post {
      either {
        val slackUserId = SlackUserId(ensureNotNull(call.parameters["slackUserId"]) { BadRequest })
        val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { BadRequest }
        service.subscribe(slackUserId, Subscription(repository, clock.now().toLocalDateTime(timeZone)))
          .mapLeft { BadRequest }.bind()
      }.respond(Created)
    }
    
    delete {
      either {
        val slackUserId = SlackUserId(ensureNotNull(call.parameters["slackUserId"]) { BadRequest })
        val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { BadRequest }
        service.unsubscribe(slackUserId, repository).mapLeft { NotFound }.bind()
      }.respond(NoContent)
    }
  }

context(PipelineContext<Unit, ApplicationCall>)
  suspend inline fun <reified A : Any> Either<HttpStatusCode, A>.respond(code: HttpStatusCode = OK): Unit =
  when (this) {
    is Either.Left -> call.respond(value)
    is Either.Right -> call.respond(code, value)
  }
