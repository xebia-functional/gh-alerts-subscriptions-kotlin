package alerts

import alerts.env.Env
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.testcontainers.containers.wait.strategy.Wait

/**
 * A singleton `PostgreSQLContainer` Test Container.
 * https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/
 *
 * ```kotlin
 * class TestClass : StringSpec({
 *   val postgres = PostgreSQLContainer.config()
 * })
 * ```
 */
class PostgreSQLContainer private constructor() :
  org.testcontainers.containers.PostgreSQLContainer<PostgreSQLContainer>("postgres:14.1-alpine") {

  companion object {
    fun config() = with(instance) {
      Env.Postgres(jdbcUrl, username, password)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
      instance.createConnection("").use { conn ->
        conn.prepareStatement("TRUNCATE users CASCADE").use {
          it.executeLargeUpdate()
        }
      }
    }

    private val instance by lazy {
      PostgreSQLContainer()
        .waitingFor(Wait.forListeningPort())
        .also(PostgreSQLContainer::start)
    }
  }
}
