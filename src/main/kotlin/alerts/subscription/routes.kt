package alerts.subscription

import alerts.badRequest
import alerts.env.Routes
import alerts.respond
import alerts.statusCode
import alerts.user.SlackUserId
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import guru.zoroark.tegral.openapi.dsl.OperationDsl
import guru.zoroark.tegral.openapi.dsl.schema
import guru.zoroark.tegral.openapi.ktor.describe
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

fun Routing.subscriptionRoutes(
  service: SubscriptionService,
  clock: Clock = Clock.System,
  timeZone: TimeZone = TimeZone.UTC,
) {
  get<Routes.Subscription> { req ->
    either {
      val subscriptions = service.findAll(req.slackUserId).mapLeft { statusCode(BadRequest) }.bind()
      Subscriptions(subscriptions)
    }.respond()
  } describe {
    slackUserId()
    OK.value response { json { schema(subscriptionsExample) } }
    BadRequest.value response { }
  }

  post<Routes.Subscription> { req ->
    either {
      val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) { statusCode(BadRequest) }
      service.subscribe(req.slackUserId, Subscription(repository, clock.now().toLocalDateTime(timeZone)))
        .mapLeft { badRequest(it.toJson(), ContentType.Application.Json) }.bind()
    }.respond(Created)
  } describe {
    slackUserId()
    repository()
    incorrectRepoBodyReturn()
    repoNotFoundReturn()
    githubErrorReturn()
    Created.value response {
      description = "Successfully subscribed to repository"
    }
    BadRequest.value response {
      json { schema<SubscriptionError>(RepoNotFound(Repository("non-existing-owner", "repo"))) }
    }
  }

  delete<Routes.Subscription> { req ->
    either {
      val repository = ensureNotNull(Either.catch { call.receive<Repository>() }.orNull()) {
        badRequest(INCORRECT_REPO_MESSAGE)
      }
      service.unsubscribe(req.slackUserId, repository).mapLeft { statusCode(NotFound) }.bind()
    }.respond(NoContent)
  } describe {
    slackUserId()
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

private fun OperationDsl.incorrectRepoBodyReturn(): Unit =
  BadRequest.value response { json { schema(INCORRECT_REPO_MESSAGE) } }

private fun OperationDsl.repoNotFoundReturn(): Unit =
  BadRequest.value response {
    json { schema(RepoNotFound(Repository("non-existing-owner", "repo"))) }
  }

private fun OperationDsl.slackUserNotFoundReturn(): Unit =
  NotFound.value response {
    json { schema(SlackUserNotFound(SlackUserId("slack-user-id"))) }
  }

private fun OperationDsl.githubErrorReturn(): Unit =
  BadRequest.value response {
    json { schema(HttpStatusCode.BadGateway) }
    description = "Github could not confirm the repository existence"
  }

private fun OperationDsl.repository(): Unit =
  body { json { schema(Repository("arrow-kt", "arrow")) } }

private fun OperationDsl.slackUserId(): Unit =
  "slackUserId" queryParameter { schema("slackUserId") }
