package alerts.persistence

import alerts.PostgreSQLContainer
import alerts.TestMetrics
import alerts.env.SqlDelight
import alerts.install
import alerts.invoke
import alerts.subscription.Repository
import alerts.subscription.SqlDelightSubscriptionsPersistence
import alerts.subscription.Subscription
import alerts.subscription.UserNotFound
import alerts.user.SlackUserId
import alerts.user.SqlDelightUserPersistence
import alerts.user.User
import alerts.user.UserId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class SubscriptionsPersistenceSpec : StringSpec({
  
  val postgres = install(PostgreSQLContainer.resource())
  val sqlDelight = install {
    SqlDelight(postgres.get().config()).bind()
  }
  val persistence = install {
    SqlDelightSubscriptionsPersistence(sqlDelight().subscriptionsQueries, sqlDelight().repositoriesQueries)
  }
  val users = install {
    SqlDelightUserPersistence(sqlDelight().usersQueries, TestMetrics.slackUsersCounter)
  }
  
  afterTest { postgres().clear() }
  
  val arrow = Repository("Arrow-kt", "arrow")
  val arrowAnalysis = Repository("Arrow-kt", "arrow-analysis")
  
  "subscribing non-existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    val userId = UserId(0L)
    persistence().subscribe(userId, subscriptions).shouldBeLeft(UserNotFound(userId))
  }
  
  "subscribing existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "subscribing user to same subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrow, LocalDateTime.now()),
    )
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "subscribing user to same subscription twice" {
    val subscriptions = listOf(Subscription(arrow, LocalDateTime.now()))
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(userId, subscriptions).shouldBeRight(Unit)
    persistence().subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "findSubscribers - empty" {
    persistence().findSubscribers(arrow).shouldBeEmpty()
  }
  
  "findSubscribers - multiple" {
    val ids = (0..10).map { num ->
      users().insertSlackUser(SlackUserId("test-user-$num"))
    }
    ids.forEach { (userId, _) ->
      persistence().subscribe(userId, listOf(Subscription(arrow, LocalDateTime.now()))).shouldBeRight(Unit)
    }
    persistence().findSubscribers(arrow).shouldBe(ids.map(User::userId))
  }
  
  "findAll - empty" {
    persistence().findAll(UserId(0L)).shouldBeEmpty()
  }
  
  "findAll - multiple" {
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence().subscribe(userId, subs).shouldBeRight(Unit)
    persistence().findAll(userId).map(Subscription::repository) shouldBe subs.map(Subscription::repository)
  }
  
  "unsubscribe - emptyList" {
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    persistence().unsubscribe(userId, emptyList()).shouldBe(Unit)
  }
  
  "unsubscribe - non-existent" {
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    persistence().unsubscribe(userId, listOf(Repository("Empty", "empty"))).shouldBe(Unit)
  }
  
  "unsubscribe - removes from database" {
    val (userId, _) = users().insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    with(persistence()) {
      subscribe(userId, subs).shouldBeRight(Unit)
      findAll(userId).map(Subscription::repository) shouldBe subs.map(Subscription::repository)
      unsubscribe(userId, subs.map { it.repository })
      findAll(userId).shouldBeEmpty()
    }
  }
})

private fun LocalDateTime.Companion.now(): LocalDateTime =
  Clock.System.now().toLocalDateTime(TimeZone.UTC)
