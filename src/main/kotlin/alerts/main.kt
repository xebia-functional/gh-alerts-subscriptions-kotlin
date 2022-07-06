package alerts

import alerts.env.Dependencies
import alerts.env.Env
import alerts.routes.healthRoute
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking(Dispatchers.Default) {
  val env = Env()
  Dependencies.resource(env).use { d ->
    embeddedServer(Netty, host = env.http.host, port = env.http.port) {
      launch { d.notifications.process() }
      routing {
        healthRoute()
      }
    }.awaitShutdown()
  }
}
