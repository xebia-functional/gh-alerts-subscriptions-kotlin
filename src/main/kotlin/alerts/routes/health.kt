package alerts.routes

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.healthRoute() =
  get("/ping") {
    call.respond("pong")
  }