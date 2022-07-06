package alerts

import alerts.env.Env
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.testcontainers.containers.wait.strategy.Wait

class PostgreSQLContainer private constructor() :
  org.testcontainers.containers.PostgreSQLContainer<PostgreSQLContainer>("postgres:14.1-alpine") {
  
  fun config() =
    Env.Postgres(jdbcUrl, username, password)
  
  suspend fun clear() = withContext(Dispatchers.IO) {
    createConnection("test-clean-up-connection").use { conn ->
      conn.prepareStatement("TRUNCATE users CASCADE").use {
        it.executeLargeUpdate()
      }
    }
  }
  
  companion object {
    fun resource(): Resource<PostgreSQLContainer> = resource {
      withContext(Dispatchers.IO) {
        PostgreSQLContainer()
          .waitingFor(Wait.forListeningPort())
          .also { container -> runInterruptible(block = container::start) }
      }
    } release { postgres ->
      withContext(Dispatchers.IO) {
        postgres.close()
      }
    }
  }
}
