package alerts.persistence

import alerts.PostgreSQLContainer
import alerts.TestMetrics
import alerts.env.sqlDelight
import alerts.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.postgresql.util.PSQLException

class SubscriptionsPersistenceSpec : StringSpec({
  
  val postgres by resource { PostgreSQLContainer() }
  val sqlDelight by resource { sqlDelight(postgres.config()) }
  val persistence by lazy {
    SubscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  }
  val users by lazy { userPersistence(sqlDelight.usersQueries, TestMetrics.slackUsersCounter) }
  
  afterTest { postgres.clear() }
  
  val arrow = Repository("Arrow-kt", "arrow")
  val arrowAnalysis = Repository("Arrow-kt", "arrow-analysis")
  val nonExistingUser = User(UserId(0L), SlackUserId("id"))
  
  "subscribing non-existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    runCatching { persistence.subscribe(nonExistingUser, subscriptions) }.shouldBeFailure<PSQLException>()
  }
  
  "subscribing existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    val user = users.insertSlackUser(SlackUserId("test-user"))
    persistence.subscribe(user, subscriptions)
  }
  
  "subscribing user to same subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrow, LocalDateTime.now()),
    )
    val user = users.insertSlackUser(SlackUserId("test-user"))
    persistence.subscribe(user, subscriptions)
  }
  
  "subscribing user to same subscription twice" {
    val subscriptions = listOf(Subscription(arrow, LocalDateTime.now()))
    val user = users.insertSlackUser(SlackUserId("test-user"))
    persistence.subscribe(user, subscriptions)
    persistence.subscribe(user, subscriptions)
  }
  
  "findSubscribers - empty" {
    persistence.findSubscribers(arrow).shouldBeEmpty()
  }
  
  "findSubscribers - multiple" {
    val ids = (0..10).map { num ->
      users.insertSlackUser(SlackUserId("test-user-$num"))
    }
    ids.forEach { user ->
      persistence.subscribe(user, listOf(Subscription(arrow, LocalDateTime.now())))
    }
    persistence.findSubscribers(arrow).shouldBe(ids.map(User::userId))
  }
  
  "findAll - empty" {
    persistence.findAll(nonExistingUser).shouldBeEmpty()
  }
  
  "findAll - multiple" {
    val user = users.insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence.subscribe(user, subs)
    persistence.findAll(user).shouldBe(subs)
  }
  
  "unsubscribe - emptyList" {
    val user = users.insertSlackUser(SlackUserId("test-user"))
    persistence.unsubscribe(user, emptyList()).shouldBe(Unit)
  }
  
  "unsubscribe - non-existent" {
    val user = users.insertSlackUser(SlackUserId("test-user"))
    persistence.unsubscribe(user, listOf(Repository("Empty", "empty"))).shouldBe(Unit)
  }
  
  "unsubscribe - removes from database" {
    val user = users.insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence.subscribe(user, subs)
    persistence.findAll(user) shouldBe subs
    persistence.unsubscribe(user, subs.map { it.repository })
    persistence.findAll(user).shouldBeEmpty()
  }
})

private fun LocalDateTime.Companion.now(): LocalDateTime =
  Clock.System.now().toLocalDateTime(TimeZone.UTC)
