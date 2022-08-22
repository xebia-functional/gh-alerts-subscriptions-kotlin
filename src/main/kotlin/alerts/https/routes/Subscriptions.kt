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
import guru.zoroark.koa.dsl.OperationBuilder
import guru.zoroark.koa.ktor.describe
import guru.zoroark.koa.dsl.schema
import io.ktor.http.ContentType
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
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  time: Time = Time.UTC,
): Route =
  route("subscription") {
    get {
      respond {
        val slackUserId = slackUserId()
        val subscriptions = service.findAll(slackUserId)
        Subscriptions(subscriptions)
      }
    } describe {
      slackUserIdQuery()
      slackUserNotFoundReturn()
      OK.value response ContentType.Application.Json.contentType {
        schema(subscriptionsExample)
        description = "Returns all subscriptions for the given slack user id"
      }
    }
    
    post {
      respond(Created) {
        val slackUserId = slackUserId()
        val repository = receiveRepository()
        service.subscribe(slackUserId, Subscription(repository, time.now()))
      }
    } describe {
      slackUserIdQuery()
      repositoryBody()
      incorrectRepoBodyReturn()
      repoNotFoundReturn()
      githubErrorReturn()
      Created.value response {
        description = "Successfully subscribed to repository"
      }
    }
    
    delete {
      respond(NoContent) {
        val slackUserId = slackUserId()
        val repository = receiveRepository()
        service.unsubscribe(slackUserId, repository)
      }
    } describe {
      slackUserIdQuery()
      repositoryBody()
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

context(EffectScope<OutgoingContent>)
  private suspend fun KtorCtx.receiveRepository(): Repository =
  effect<OutgoingContent, Repository> { call.receive() }
    .attempt { _: ContentTransformationException -> shift(badRequest(INCORRECT_REPO_MESSAGE)) }
    .bind()

private val subscriptionsExample =
  Subscriptions(listOf(Subscription(Repository("arrow-kt", "arrow"), Clock.System.now().toLocalDateTime(TimeZone.UTC))))

private fun OperationBuilder.incorrectRepoBodyReturn(): Unit =
  BadRequest.value response ContentType.Application.Json.contentType { schema(INCORRECT_REPO_MESSAGE) }

private fun OperationBuilder.repoNotFoundReturn(): Unit =
  BadRequest.value response ContentType.Application.Json.contentType {
    schema(RepoNotFound(Repository("non-existing-owner", "repo")))
  }

private fun OperationBuilder.slackUserNotFoundReturn(): Unit =
  NotFound.value response ContentType.Application.Json.contentType {
    schema(SlackUserNotFound(SlackUserId("slack-user-id")))
  }

private fun OperationBuilder.githubErrorReturn(): Unit =
  BadRequest.value response ContentType.Application.Json.contentType {
    schema(HttpStatusCode.BadGateway)
    description = "Github could not confirm the repository existence"
  }

private fun OperationBuilder.repositoryBody(): Unit =
  "repository" requestBody { schema(Repository("arrow-kt", "arrow")) }

private fun OperationBuilder.slackUserIdQuery(): Unit =
  "slackUserId" queryParameter { schema("slackUserId") }
