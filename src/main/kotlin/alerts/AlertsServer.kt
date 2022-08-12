package alerts

import alerts.env.Dependencies
import alerts.https.routes.healthRoute
import alerts.https.routes.metricsRoute
import alerts.https.routes.slackRoutes
import alerts.https.routes.subscriptionRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun Application.alertsServer(dependencies: Dependencies) {
  configure()
  metrics(dependencies)
  routes(dependencies)
}

fun Application.configure() {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    )
  }
}

private fun Application.metrics(dependencies: Dependencies) {
  install(MicrometerMetrics) {
    registry = dependencies.metrics
  }
}

private fun Application.routes(dependencies: Dependencies): Routing =
  routing {
    healthRoute()
    metricsRoute(dependencies.metrics)
    subscriptionRoutes(dependencies.subscriptions)
    slackRoutes(dependencies.subscriptions)
  }
