package alerts.user

import alerts.IntegrationTestBase
import alerts.TestMetrics
import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.transaction.reactive.TransactionalOperator

class UserPersistenceSpec(
    private val userRepo: UserRepo,
    private val transactionOperator: TransactionalOperator
) : IntegrationTestBase({

    val slackUserId = SlackUserId("test-user-id")
    val persistence = DefaultUserPersistence(userRepo, TestMetrics.slackUsersCounter, transactionOperator)

    afterTest {
        userRepo.deleteAll()
    }

    "Insert user" {
        persistence.insertSlackUser(slackUserId).slackUserId shouldBe slackUserId
    }

    "Insert user twice doesn't fails but is idempotent" {
        val user = persistence.insertSlackUser(slackUserId)
        persistence.insertSlackUser(slackUserId) shouldBe user
    }

    "find non-existing user results in null" {
        persistence.find(UserId(0L)).shouldBeNull()
    }

    "find existing user" {
        val user = persistence.insertSlackUser(slackUserId)
        persistence.find(user.userId).shouldNotBeNull() shouldBe user
    }

    "findSlackUser non-existing user results in null" {
        persistence.findSlackUser(SlackUserId("other-user")).shouldBeNull()
    }

    "findSlackUser existing user" {
        val user = persistence.insertSlackUser(slackUserId)
        persistence.findSlackUser(slackUserId).shouldNotBeNull() shouldBe user
    }

    "findUsers all non-existing is empty" {
        val ids = nonEmptyListOf(UserId(0L), UserId(1L), UserId(2L))
        persistence.findUsers(ids).shouldBeEmpty()
    }

    "findUsers existing users" {
        val users = nonEmptyListOf(
            persistence.insertSlackUser(slackUserId),
            persistence.insertSlackUser(SlackUserId("test-user-id-2")),
            persistence.insertSlackUser(SlackUserId("test-user-id-3"))
        )
        val ids = users.map { it.userId }
        persistence.findUsers(ids) shouldBe users
    }

    "Increment counter on new user" {
        val original = TestMetrics.slackUsersCounter.get()
        persistence.insertSlackUser(slackUserId)
        TestMetrics.slackUsersCounter.get() shouldBe original + 1
    }

    "Doesn't increment counter for existing user" {
        val original = TestMetrics.slackUsersCounter.get()
        persistence.insertSlackUser(slackUserId)
        val now = TestMetrics.slackUsersCounter.get()
        now shouldBe original + 1
        persistence.insertSlackUser(slackUserId)
        TestMetrics.slackUsersCounter.get() shouldBe now
    }
})
