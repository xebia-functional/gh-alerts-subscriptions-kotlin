package alerts.https.routes

import alerts.Time
import alerts.or
import alerts.persistence.Repository
import alerts.persistence.Subscription
import alerts.respond
import alerts.service.SubscriptionService
import alerts.slackUserId
import arrow.core.Either
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  time: Time = Time.UTC
): Route =
  route("subscription") {
    get {
      respond {
        val slackUserId = slackUserId()
        val subscriptions = service.findAll(slackUserId).or(BadRequest)
        Subscriptions(subscriptions)
      }
    }
    
    post {
      respond(Created) {
        val slackUserId = slackUserId()
        val repository = Either.catch<Repository> { call.receive() }.or(BadRequest)
        service.subscribe(slackUserId, Subscription(repository, time.now())).or(BadRequest)
      }
    }
    
    delete {
      respond(NoContent) {
        val slackUserId = slackUserId()
        val repository = Either.catch<Repository> { call.receive() }.or(BadRequest)
        service.unsubscribe(slackUserId, repository).or(NotFound)
      }
    }
  }
