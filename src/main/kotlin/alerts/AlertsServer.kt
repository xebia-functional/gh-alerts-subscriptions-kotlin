package alerts

import alerts.env.Dependencies
import alerts.https.routes.healthRoute
import alerts.https.routes.metricsRoute
import alerts.https.routes.openApiRoutes
import alerts.https.routes.slackRoutes
import alerts.https.routes.subscriptionRoutes
import guru.zoroark.koa.ktor.Koa
import guru.zoroark.koa.ktor.respondOpenApiDocument
import guru.zoroark.koa.ktor.ui.KoaSwaggerUi
import guru.zoroark.koa.ktor.ui.swaggerUi
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.Logger
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
  install(Koa) {
    title = "GitHub alerts API"
    version = "v1"
  }
  install(KoaSwaggerUi)
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
    openApiRoutes()
  }
