package alerts.persistence

import alerts.PostgreSQLContainer
import alerts.env.sqlDelight
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UserPersistenceSpec : StringSpec({
  val slackUserId = SlackUserId("test-user-id")
  val postgres by resource(PostgreSQLContainer.resource())
  val persistence by resource(arrow.fx.coroutines.continuations.resource {
    val sqlDelight = sqlDelight(postgres.config()).bind()
    userPersistence(sqlDelight.usersQueries)
  })
  
  afterTest { postgres.clear() }
  
  "Insert user" {
    persistence.insertSlackUser(slackUserId).shouldBeRight().slackUserId shouldBe slackUserId
  }
  
  "Insert user twice fails" {
    persistence.insertSlackUser(slackUserId)
    persistence.insertSlackUser(slackUserId).shouldBeLeft(UserAlreadyExists(slackUserId))
  }
  
  "find non-existing user results in null" {
    persistence.find(UserId(0L)).shouldBeNull()
  }
  
  "find existing user" {
    val user = persistence.insertSlackUser(slackUserId).shouldBeRight()
    persistence.find(user.userId).shouldNotBeNull() shouldBe user
  }
  
  "findSlackUser non-existing user results in null" {
    persistence.findSlackUser(SlackUserId("other-user")).shouldBeNull()
  }
  
  "findSlackUser existing user" {
    val user = persistence.insertSlackUser(slackUserId).shouldBeRight()
    persistence.findSlackUser(slackUserId).shouldNotBeNull() shouldBe user
  }
  
  "findUsers all non-existing is empty" {
    val ids = nonEmptyListOf(UserId(0L), UserId(1L), UserId(2L))
    persistence.findUsers(ids).shouldBeEmpty()
  }
  
  "findUsers existing users" {
    val users = nonEmptyListOf(
      persistence.insertSlackUser(slackUserId).shouldBeRight(),
      persistence.insertSlackUser(SlackUserId("test-user-id-2")).shouldBeRight(),
      persistence.insertSlackUser(SlackUserId("test-user-id-3")).shouldBeRight(),
    )
    val ids = users.map { it.userId }
    persistence.findUsers(ids) shouldBe users
  }
})
