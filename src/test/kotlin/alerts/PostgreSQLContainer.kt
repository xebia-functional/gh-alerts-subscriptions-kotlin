package alerts

import alerts.env.Env

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

    private val instance by lazy {
      PostgreSQLContainer()
        .also(PostgreSQLContainer::start)
    }
  }
}
