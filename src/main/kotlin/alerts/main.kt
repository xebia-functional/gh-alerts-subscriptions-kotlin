package alerts

import alerts.env.Dependencies
import alerts.env.Env
import alerts.routes.healthRoute
import arrow.continuations.SuspendApp
import arrow.fx.coroutines.continuations.resource
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resource {
    val dependencies = Dependencies.resource(env).bind()
    server(Netty, port = env.http.port, host = env.http.host) {
      routing {
        healthRoute()
      }
    }.bind()
    dependencies.notifications.process().bind()
  }.use { awaitCancellation() }
}
