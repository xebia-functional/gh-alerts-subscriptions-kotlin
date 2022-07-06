package alerts.persistence

import alerts.PostgreSQLContainer
import alerts.env.sqlDelight
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class SubscriptionsPersistenceSpec : StringSpec({
  
  val postgres by resource(PostgreSQLContainer.resource())
  val sqlDelight by resource(arrow.fx.coroutines.continuations.resource {
    sqlDelight(postgres.config()).bind()
  })
  val persistence by lazy {
    subscriptionsPersistence(sqlDelight.subscriptionsQueries, sqlDelight.repositoriesQueries)
  }
  val users by lazy { userPersistence(sqlDelight.usersQueries) }
  
  afterTest { postgres.clear() }
  
  val arrow = Repository("Arrow-kt", "arrow")
  val arrowAnalysis = Repository("Arrow-kt", "arrow-analysis")
  
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
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "subscribing user to same subscription" {
    val subscriptions = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrow, LocalDateTime.now()),
    )
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "subscribing user to same subscription twice" {
    val subscriptions = listOf(Subscription(arrow, LocalDateTime.now()))
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
    persistence.subscribe(userId, subscriptions).shouldBeRight(Unit)
  }
  
  "findSubscribers - empty" {
    persistence.findSubscribers(arrow).shouldBeEmpty()
  }
  
  "findSubscribers - multiple" {
    val ids = (0..10).map { num ->
      users.insertSlackUser(SlackUserId("test-user-$num")).shouldBeRight()
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
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence.subscribe(userId, subs).shouldBeRight(Unit)
    persistence.findAll(userId).shouldBe(subs)
  }
  
  "unsubscribe - emptyList" {
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    persistence.unsubscribe(userId, emptyList()).shouldBe(Unit)
  }
  
  "unsubscribe - non-existent" {
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    persistence.unsubscribe(userId, listOf(Repository("Empty", "empty"))).shouldBe(Unit)
  }
  
  "unsubscribe - removes from database" {
    val (userId, _) = users.insertSlackUser(SlackUserId("test-user")).shouldBeRight()
    val subs = listOf(
      Subscription(arrow, LocalDateTime.now()),
      Subscription(arrowAnalysis, LocalDateTime.now())
    )
    persistence.subscribe(userId, subs).shouldBeRight(Unit)
    persistence.findAll(userId) shouldBe subs
    persistence.unsubscribe(userId, subs.map { it.repository })
    persistence.findAll(userId).shouldBeEmpty()
  }
})
