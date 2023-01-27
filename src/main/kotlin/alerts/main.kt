package alerts

import alerts.env.Dependencies
import alerts.env.Env
import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = Dependencies(env)
    dependencies.notifications.process().bind()
    server(Netty, port = env.http.port, host = env.http.host) {
      alertsServer(dependencies)
    }
    awaitCancellation()
  }
}
