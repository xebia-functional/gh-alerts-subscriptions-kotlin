package alerts.https.routes

import alerts.badRequest
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.respond
import alerts.service.RepoNotFound
import alerts.service.SubscriptionError
import alerts.service.SubscriptionService
import alerts.service.UserNotFound
import alerts.statusCode
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
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
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
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
  timeZone: TimeZone = TimeZone.UTC
) {
  get<Routes.Subscription> { req ->
    either {
      val subscriptions = service.findAll(req.slackUserId).mapLeft { statusCode(BadRequest) }.bind()
      Subscriptions(subscriptions)
    }.respond()
  } describe {
    slackUserIdQuery()
    slackUserNotFoundReturn()
    OK.value response ContentType.Application.Json.contentType {
      schema(subscriptionsExample)
      description = "Returns all subscriptions for the given slack user id"
    }
  }
  
  post<Routes.Subscription> { req ->
    either {
      val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { statusCode(BadRequest) }
      service.subscribe(req.slackUserId, Subscription(repository, clock.now().toLocalDateTime(timeZone)))
        .mapLeft { badRequest(it.toJson(), ContentType.Application.Json) }.bind()
    }.respond(Created)
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
  
  delete<Routes.Subscription> { req ->
    either {
      val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { statusCode(BadRequest) }
      service.unsubscribe(req.slackUserId, repository).mapLeft { statusCode(NotFound) }.bind()
    }.respond(NoContent)
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

private val subscriptionsExample =
  Subscriptions(listOf(Subscription(Repository("arrow-kt", "arrow"), Clock.System.now().toLocalDateTime(TimeZone.UTC))))

private const val INCORRECT_REPO_MESSAGE =
  "The body of the request must be a JSON object with an 'owner', and 'name' field."

private fun OperationBuilder.incorrectRepoBodyReturn(): Unit =
  BadRequest.value response ContentType.Application.Json.contentType { schema(INCORRECT_REPO_MESSAGE) }

private fun OperationBuilder.repoNotFoundReturn(): Unit =
  BadRequest.value response ContentType.Application.Json.contentType {
    schema(RepoNotFound(Repository("non-existing-owner", "repo")))
  }

private fun OperationBuilder.slackUserNotFoundReturn(): Unit =
  NotFound.value response ContentType.Application.Json.contentType {
    schema(UserNotFound(SlackUserId("slack-user-id")))
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
