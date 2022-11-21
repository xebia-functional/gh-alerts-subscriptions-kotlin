package alerts.health

import alerts.env.Routes
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing

fun Routing.healthRoute() =
  get<Routes.Health> { call.respond("pong") }
