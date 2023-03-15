package alerts.subscription

import alerts.github.GithubClient
import alerts.user.SlackUserId
import alerts.user.User
import alerts.user.UserId
import alerts.user.UserPersistence
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.random.Random

@Suppress("MatchingDeclarationName")
class SubscriptionControllerSpec : StringSpec({
    val testRepo = Repository("foo", "bar")
    val controller = SubscriptionController(service, clock, timeZone)

    suspend fun <A> subscriptions(test: suspend WebTestClient.() -> A): A = with(
        WebTestClient.bindToController(controller).configureClient().build()
    ) { test() }

    "GET /subscription?slackUserId=41 returns status code 200" {
        subscriptions {
            get().uri("/subscription?slackUserId=42").exchange().expectStatus()
        }.isOk
    }

    "GET /subscription?slackUserId=42 returns empty list" {
        subscriptions {
            get().uri("/subscription?slackUserId=42").exchange().expectBody()
        }.json("{\"subscriptions\":[]}")
    }

    "POST /subscription?slackUserId=42 returns status code 201" {
        subscriptions {
            post().uri("/subscription?slackUserId=42")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(testRepo)
                .exchange().expectStatus()
        }.isCreated
    }

    "DELETE /subscription?slackUserId=42 returns status code 204" {
        subscriptions {
            deleteWithBody().uri("/subscription?slackUserId=42")
                .accept(MediaType.APPLICATION_JSON).bodyValue(testRepo)
                .exchange().expectStatus()
        }.isNoContent
    }
})

private fun WebTestClient.deleteWithBody() = method(HttpMethod.DELETE)

private val client = GithubClient { _, _ -> true.right() }

private val subscriptions = object : SubscriptionsPersistence {
    override suspend fun findAll(user: UserId): List<Subscription> = emptyList()
    override suspend fun findSubscribers(repository: Repository): List<UserId> = emptyList()
    override suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit> =
        Unit.right()

    override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) = Unit
}

private val users = object : UserPersistence {
    override suspend fun insertSlackUser(slackUserId: SlackUserId): User = User(UserId(Random.nextLong()), slackUserId)

    override suspend fun find(userId: UserId): User = User(UserId(Random.nextLong()), SlackUserId(""))

    override suspend fun findSlackUser(slackUserId: SlackUserId): User = User(UserId(Random.nextLong()), slackUserId)

    override suspend fun findUsers(userIds: NonEmptyList<UserId>): List<User> = emptyList()
}

private val subscriptionProducer = object : SubscriptionProducer {
    override suspend fun publish(repo: Repository) = Unit
    override suspend fun delete(repo: Repository) = Unit
}

private val service = SpringSubscriptionService(subscriptions, users, subscriptionProducer, client)

private val clock: Clock = Clock.System

private val timeZone: TimeZone = TimeZone.UTC
