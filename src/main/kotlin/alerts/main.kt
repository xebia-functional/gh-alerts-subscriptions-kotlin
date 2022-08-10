package alerts

import alerts.env.Dependencies
import alerts.env.Env
import alerts.routes.healthRoute
import alerts.routes.metricsRoute
import arrow.continuations.SuspendApp
import arrow.fx.coroutines.continuations.resource
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resource {
    val dependencies = Dependencies.resource(env).bind()
    dependencies.notifications.process().bind()
    val engine = server(Netty, port = env.http.port, host = env.http.host).bind()
    engine.application.install(MicrometerMetrics) {
      registry = dependencies.metrics
    }
    engine.application.routing {
      healthRoute()
      metricsRoute(dependencies.metrics)
    }
  }.use { awaitCancellation() }
}
