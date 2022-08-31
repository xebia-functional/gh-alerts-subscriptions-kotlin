package alerts.https.routes

import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Routing.metricsRoute(metrics: PrometheusMeterRegistry) =
  get<Routes.Metrics> { call.respond(metrics.scrape()) }
