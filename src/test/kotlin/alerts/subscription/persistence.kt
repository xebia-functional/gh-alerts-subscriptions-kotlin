package alerts.subscription

import alerts.IntegrationTestBase
import alerts.TestMetrics
import alerts.now
import alerts.user.DefaultUserPersistence
import alerts.user.SlackUserId
import alerts.user.User
import alerts.user.UserId
import alerts.user.UserRepo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import org.springframework.transaction.reactive.TransactionalOperator

class SubscriptionPersistenceSpec(
    private val userRepo: UserRepo,
    private val subscriptionRepo: SubscriptionRepo,
    private val repositoryRepo: RepositoryRepo,
    private val transactionOperator: TransactionalOperator
) : IntegrationTestBase({

    val arrow = Repository("Arrow-kt", "arrow")
    val arrowAnalysis = Repository("Arrow-kt", "arrow-analysis")
    val persistence = DefaultSubscriptionsPersistence(subscriptionRepo, repositoryRepo, transactionOperator)
    val users = DefaultUserPersistence(userRepo, TestMetrics.slackUsersCounter, transactionOperator)

    afterTest {
        userRepo.deleteAll()
    }

    "subscribing non-existent user to subscription" {
        val subscriptions = listOf(
            Subscription(arrow, LocalDateTime.now()),
            Subscription(arrowAnalysis, LocalDateTime.now())
        )
        val userId = UserId(0L)
        persistence.subscribe(userId, subscriptions).shouldBeLeft(UserNotFound(userId))
    }

    "subscribing existent user to subscription" {
        val subscriptions = listOf(
            Subscription(arrow, LocalDateTime.now()),
            Subscription(arrowAnalysis, LocalDateTime.now())
        )
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
    }

    "subscribing user to same subscription" {
        val subscriptions = listOf(
            Subscription(arrow, LocalDateTime.now()),
            Subscription(arrow, LocalDateTime.now()),
        )
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
    }

    "subscribing user to same subscription twice" {
        val subscriptions = listOf(Subscription(arrow, LocalDateTime.now()))
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
        persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
    }

    "findSubscribers - empty" {
        persistence.findSubscribers(arrow).shouldBeEmpty()
    }

    "findSubscribers - multiple" {
        val ids = (0..10).map { num ->
            users.insertSlackUser(SlackUserId("test-user-$num"))
        }
        ids.forEach { (userId, _) ->
            persistence.subscribe(userId, listOf(Subscription(arrow, LocalDateTime.now()))).shouldBeRight(Unit)
        }
        persistence.findSubscribers(arrow).shouldBe(ids.map(User::userId))
    }

    "findAll - empty" {
        persistence.findAll(UserId(0L)).shouldBeEmpty()
    }

    "findAll - multiple" {
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        val subs = listOf(
            Subscription(arrow, LocalDateTime.now()),
            Subscription(arrowAnalysis, LocalDateTime.now())
        )
        persistence.subscribe(userId, subs).shouldBeRight(Unit)
        persistence.findAll(userId).map(Subscription::repository) shouldBe subs.map(Subscription::repository)
    }

    "unsubscribe - emptyList" {
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        persistence.unsubscribe(userId, emptyList()).shouldBe(Unit)
    }

    "unsubscribe - non-existent" {
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        persistence.unsubscribe(userId, listOf(Repository("Empty", "empty"))).shouldBe(Unit)
    }

    "unsubscribe - removes from database" {
        val (userId, _) = users.insertSlackUser(SlackUserId("test-user"))
        val subs = listOf(
            Subscription(arrow, LocalDateTime.now()),
            Subscription(arrowAnalysis, LocalDateTime.now())
        )
        with(persistence) {
            subscribe(userId, subs).shouldBeRight(Unit)
            findAll(userId).map(Subscription::repository) shouldBe subs.map(Subscription::repository)
            unsubscribe(userId, subs.map { it.repository })
            findAll(userId).shouldBeEmpty()
        }
    }
})
