package alerts

import alerts.env.Dependencies
import alerts.metrics.metricsRoute
import alerts.openapi.openApiRoutes
import alerts.slack.slackRoutes
import alerts.subscription.subscriptionRoutes
import com.sksamuel.cohort.ktor.Cohort
import guru.zoroark.tegral.openapi.ktor.TegralOpenApiKtor
import guru.zoroark.tegral.openapi.ktorui.TegralSwaggerUiKtor
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.resources.Resources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun Application.alertsServer(dependencies: Dependencies) {
  configure()
  healthChecks(dependencies)
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
  install(Resources)
  install(TegralOpenApiKtor) {
    title = "GitHub alerts API"
    version = "v1"
  }
  install(TegralSwaggerUiKtor)
}

private fun Application.healthChecks(dependencies: Dependencies) {
  install(Cohort) {
    healthcheck("/readiness", dependencies.healthCheck)
    healthcheck("/health", dependencies.healthCheck)
  }
}

private fun Application.metrics(dependencies: Dependencies) {
  install(MicrometerMetrics) {
    registry = dependencies.metrics
  }
}

private fun Application.routes(dependencies: Dependencies): Routing =
  routing {
    metricsRoute(dependencies.metrics)
    subscriptionRoutes(dependencies.subscriptions)
    slackRoutes(dependencies.subscriptions)
    openApiRoutes()
  }
