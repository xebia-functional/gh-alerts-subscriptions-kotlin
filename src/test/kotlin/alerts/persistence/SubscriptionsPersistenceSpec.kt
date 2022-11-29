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
import arrow.core.left
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class SubscriptionsPersistenceSpec : StringSpec({
  
  val postgres = install { PostgreSQLContainer() }
  val sqlDelight = install {
    SqlDelight(postgres.get().config())
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
  val nonExistingUser = User(UserId(0L), SlackUserId("id"))
  
  "subscribing non-existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    val expected = UserNotFound(User(UserId(0), SlackUserId("id"))).left()
    persistence().subscribe(nonExistingUser, subscriptions) shouldBe expected
  }
  
  "subscribing existent user to subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    val user = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(user, subscriptions)
  }
  
  "subscribing user to same subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrow, LocalDateTime.now()),
    )
    val user = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(user, subscriptions)
  }
  
  "subscribing user to same subscription twice" {
    val subscriptions = listOf(Subscription(arrow, LocalDateTime.now()))
    val user = users().insertSlackUser(SlackUserId("test-user"))
    persistence().subscribe(user, subscriptions)
    persistence().subscribe(user, subscriptions)
  }
  
  "findSubscribers - empty" {
    persistence().findSubscribers(arrow).shouldBeEmpty()
  }
  
  "findSubscribers - multiple" {
    val ids = (0..10).map { num ->
      users().insertSlackUser(SlackUserId("test-user-$num"))
    }
    ids.forEach { user ->
      persistence().subscribe(user, listOf(Subscription(arrow, LocalDateTime.now())))
    }
    persistence().findSubscribers(arrow).shouldBe(ids.map(User::userId))
  }
  
  "findAll - empty" {
    persistence().findAll(nonExistingUser).shouldBeEmpty()
  }
  
  "findAll - multiple" {
    val user = users().insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence().subscribe(user, subs)
    persistence().findAll(user).shouldBe(subs)
  }
  
  "unsubscribe - emptyList" {
    val user = users().insertSlackUser(SlackUserId("test-user"))
    persistence().unsubscribe(user, emptyList()).shouldBe(Unit)
  }
  
  "unsubscribe - non-existent" {
    val user = users().insertSlackUser(SlackUserId("test-user"))
    persistence().unsubscribe(user, listOf(Repository("Empty", "empty"))).shouldBe(Unit)
  }
  
  "unsubscribe - removes from database" {
    val user = users().insertSlackUser(SlackUserId("test-user"))
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    with(persistence()) {
      subscribe(user, subs)
      findAll(user) shouldBe subs
      unsubscribe(user, subs.map { it.repository })
      findAll(user).shouldBeEmpty()
    }
  }
})

private fun LocalDateTime.Companion.now(): LocalDateTime =
  Clock.System.now().toLocalDateTime(TimeZone.UTC)
