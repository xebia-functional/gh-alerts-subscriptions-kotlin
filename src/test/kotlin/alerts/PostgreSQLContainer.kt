package alerts

import alerts.env.Env
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromAutoCloseable
import arrow.fx.coroutines.release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.testcontainers.containers.wait.strategy.Wait

suspend fun ResourceScope.PostgreSQLContainer(): PostgreSQLContainer =
 PostgreSQLContainer.resource().bind()

class PostgreSQLContainer private constructor() :
  org.testcontainers.containers.PostgreSQLContainer<PostgreSQLContainer>("postgres:14.1-alpine") {
  
  fun config() =
    Env.Postgres(jdbcUrl, username, password)
  
  suspend fun clear() = withContext(Dispatchers.IO) {
    createConnection("").use { conn ->
      conn.prepareStatement("TRUNCATE users CASCADE").use {
        it.executeLargeUpdate()
      }
    }
  }
  
  companion object {
    fun resource(): Resource<PostgreSQLContainer> =
      Resource({
        withContext(Dispatchers.IO) {
          PostgreSQLContainer()
            .waitingFor(Wait.forListeningPort())
            .also { container -> runInterruptible(block = container::start) }
        }
      }) { container, _ ->
        withContext(Dispatchers.IO) { container.close() }
      }
  }
}
