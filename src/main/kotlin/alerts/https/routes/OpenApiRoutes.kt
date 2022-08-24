package alerts.https.routes

import guru.zoroark.tegral.openapi.ktor.openApiEndpoint
import guru.zoroark.tegral.openapi.ktorui.swaggerUiEndpoint
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.openApiRoutes() {
  openApiEndpoint("/openapi")
  swaggerUiEndpoint(path = "/swagger", openApiPath = "/openapi")
  get("/swagger") {
    call.respondRedirect("/swagger/index.html")
  }
}
