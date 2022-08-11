package alerts

import alerts.env.Dependencies
import alerts.env.Env
import arrow.continuations.SuspendApp
import arrow.core.identity
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = Dependencies(env)
    dependencies.notifications.process()
    server(Netty, port = env.http.port, host = env.http.host)
      .application.alertsServer(dependencies)
    awaitCancellation()
  }
}
