package alerts.https.routes

import guru.zoroark.tegral.openapi.ktor.openApiEndpoint
import guru.zoroark.tegral.openapi.ktorui.swaggerUiEndpoint
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing

fun Routing.openApiRoutes() {
  openApiEndpoint("/openapi")
  swaggerUiEndpoint(path = "/swagger", openApiPath = "/openapi")
  get<Routes.Swagger> {
    call.respondRedirect("/swagger/index.html")
  }
}
