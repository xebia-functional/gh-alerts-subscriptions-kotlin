package alerts

import alerts.env.Env
import alerts.env.sqlDelight
import alerts.routes.healthRoute
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking(Dispatchers.Default) {
  val env = Env()
  sqlDelight(env.postgres).use {
    embeddedServer(Netty, host = env.http.host, port = env.http.port) {
      routing {
        healthRoute()
      }
    }.awaitShutdown()
  }
}