package alerts

import alerts.env.Dependencies
import alerts.env.Env
import arrow.continuations.SuspendApp
import arrow.fx.coroutines.continuations.resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resource {
    val dependencies = Dependencies.resource(env).bind()
    dependencies.notifications.process().bind()
    server(Netty, port = env.http.port, host = env.http.host).bind()
      .application.alertsServer(dependencies)
  }.use { awaitCancellation() }
}
