package alerts.persistence

import alerts.PostgreSQLContainer
import alerts.env.sqlDelight
import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.postgresql.util.PSQLException

class UserPersistenceSpec : StringSpec({
  val postgresConfig = PostgreSQLContainer.config()
  val persistence by resource(sqlDelight(postgresConfig).map {
    userPersistence(it.usersQueries)
  })

  "Insert user" {
    val slackUserId = SlackUserId("test-user-id")
    persistence.insertSlackUser(slackUserId).slackUserId shouldBe slackUserId
  }

  "Insert user twice fails" {
    val slackUserId = SlackUserId("test-user-id")
    persistence.insertSlackUser(slackUserId).slackUserId shouldBe slackUserId
    Either.catch {
      persistence.insertSlackUser(slackUserId)
    }.shouldBeLeft()
  }
})
