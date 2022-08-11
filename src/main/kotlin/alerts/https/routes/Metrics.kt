package alerts.https.routes

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Routing.metricsRoute(metrics: PrometheusMeterRegistry) =
  get("/metrics") {
    call.respond(metrics.scrape())
  }
