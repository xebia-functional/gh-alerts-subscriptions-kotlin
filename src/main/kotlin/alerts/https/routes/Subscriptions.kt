package alerts.https.routes

import alerts.KtorCtx
import alerts.Time
import alerts.attempt
import alerts.badRequest
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.respond
import alerts.service.RepoNotFound
import alerts.service.SlackUserNotFound
import alerts.service.SubscriptionService
import alerts.slackUserId
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import guru.zoroark.tegral.openapi.dsl.OperationDsl
import guru.zoroark.tegral.openapi.dsl.schema
import guru.zoroark.tegral.openapi.ktor.describe
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.call
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  time: Time = Time.UTC,
) {
    get<Routes.Subscription> {
      respond {
        val slackUserId = slackUserId()
        val subscriptions = service.findAll(slackUserId)
        Subscriptions(subscriptions)
      }
    } describe {
      slackUserIdQuery()
      slackUserNotFoundReturn()
      OK.value response {
        json { schema(subscriptionsExample) }
        description = "Returns all subscriptions for the given slack user id"
      }
    }

    post<Routes.Subscription> {
      respond(Created) {
        val slackUserId = slackUserId()
        val repository = receiveRepository()
        service.subscribe(slackUserId, Subscription(repository, time.now()))
      }
    } describe {
      slackUserIdQuery()
      repository()
      incorrectRepoBodyReturn()
      repoNotFoundReturn()
      githubErrorReturn()
      Created.value response {
        description = "Successfully subscribed to repository"
      }
    }
    
    delete<Routes.Subscription> {
      respond(NoContent) {
        val slackUserId = slackUserId()
        val repository = receiveRepository()
        service.unsubscribe(slackUserId, repository)
      }
    } describe {
      slackUserIdQuery()
      repository()
      incorrectRepoBodyReturn()
      repoNotFoundReturn()
      slackUserNotFoundReturn()
      NoContent.value response {
        description = "Deleted the subscription for the given user."
      }
    }
  }

private const val INCORRECT_REPO_MESSAGE =
  "The body of the request must be a JSON object with an 'owner', and 'name' field."

private val subscriptionsExample =
  Subscriptions(listOf(Subscription(Repository("arrow-kt", "arrow"), Clock.System.now().toLocalDateTime(TimeZone.UTC))))

context(EffectScope<OutgoingContent>)
  private suspend fun KtorCtx.receiveRepository(): Repository =
  effect<OutgoingContent, Repository> { call.receive() }
    .attempt { _: ContentTransformationException -> shift(badRequest(INCORRECT_REPO_MESSAGE)) }
    .bind()

private fun OperationDsl.githubErrorReturn(): Unit =
  BadRequest.value response {
    json { schema(HttpStatusCode.BadGateway) }
    description = "Github could not confirm the repository existence"
  }

private fun OperationDsl.incorrectRepoBodyReturn(): Unit =
  BadRequest.value response { json { schema(INCORRECT_REPO_MESSAGE) } }

private fun OperationDsl.repoNotFoundReturn(): Unit =
  BadRequest.value response {
    json { schema(RepoNotFound(Repository("non-existing-owner", "repo"))) }
  }

private fun OperationDsl.repository(): Unit =
  body { json { schema(Repository("arrow-kt", "arrow")) } }

private fun OperationDsl.slackUserIdQuery(): Unit =
  "slackUserId" queryParameter { schema("slackUserId") }

private fun OperationDsl.slackUserNotFoundReturn(): Unit =
  NotFound.value response {
    json { schema(SlackUserNotFound(SlackUserId("slack-user-id"))) }
  }
