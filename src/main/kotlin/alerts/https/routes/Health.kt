package alerts.https.routes

import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.healthRoute() =
  get<Routes.Health> { call.respond("pong") }
