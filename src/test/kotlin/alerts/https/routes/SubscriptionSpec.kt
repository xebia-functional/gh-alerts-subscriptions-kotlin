package alerts.https.routes

import alerts.https.client.GithubClient
import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.User
import alerts.persistence.UserId
import alerts.persistence.UserNotFound
import alerts.persistence.UserPersistence
import alerts.service.SubscriptionService
import alerts.testApp
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import kotlin.random.Random

class SubscriptionSpec : StringSpec({
  suspend fun <A> subscriptions(test: suspend HttpClient.() -> A): A =
    testApp({
      routing { subscriptionRoutes(service) }
    }, test)
  
  "GET /subscription?slackUserId=42 returns status code 200" {
    subscriptions {
      get("/subscription?slackUserId=42").status
    } shouldBe OK
  }
  
  "GET /subscription?slackUserId=42 returns emtpy list" {
    subscriptions {
      get("/subscription?slackUserId=42").bodyAsText()
    } shouldBe "{\"subscriptions\":[]}"
  }
  
  "POST /subscription?slackUserId=42 returns status code 201" {
    subscriptions {
      post("/subscription?slackUserId=42") { testRepo() }.status
    } shouldBe Created
  }
  
  "DELETE /subscription?slackUserId=42 returns status code 204" {
    subscriptions {
      delete("/subscription?slackUserId=42") { testRepo() }.status
    } shouldBe NoContent
  }
})

private val client = GithubClient { _, _ -> true.right() }

private val subscriptions = object : SubscriptionsPersistence {
  override suspend fun findAll(user: User): List<Subscription> = emptyList()
  override suspend fun findSubscribers(repository: Repository): List<UserId> = emptyList()
  override suspend fun subscribe(user: User, subscription: List<Subscription>): Unit = Unit
  override suspend fun unsubscribe(user: User, repositories: List<Repository>): Unit = Unit
}

private val users = object : UserPersistence {
  override suspend fun insertSlackUser(slackUserId: SlackUserId): User =
    User(UserId(Random.nextLong()), slackUserId)
  
  override suspend fun find(userId: UserId): User =
    User(UserId(Random.nextLong()), SlackUserId(""))
  
  override suspend fun findSlackUser(slackUserId: SlackUserId): User =
    User(UserId(Random.nextLong()), slackUserId)
  
  override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> = emptyList()
}

private val subscriptionProducer = object : SubscriptionProducer {
  override suspend fun publish(repo: Repository) = Unit
  override suspend fun delete(repo: Repository) = Unit
}

private val service = SubscriptionService(subscriptions, users, subscriptionProducer, client)

private fun HttpRequestBuilder.testRepo() {
  contentType(ContentType.Application.Json)
  setBody(Repository("foo", "bar"))
}
