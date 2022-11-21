package alerts

import alerts.env.Dependencies
import alerts.env.Env
import arrow.continuations.SuspendApp
import arrow.fx.coroutines.continuations.resource
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resource {
    val dependencies = Dependencies(env).bind()
    dependencies.notifications.process().bind()
    server(Netty, port = env.http.port, host = env.http.host).bind()
      .application.alertsServer(dependencies)
  }.use { awaitCancellation() }
}
