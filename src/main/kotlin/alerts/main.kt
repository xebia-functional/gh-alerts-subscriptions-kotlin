package alerts

import io.ktor.server.application.call
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(): Unit {
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/ping") {
                call.respond("pong")
            }
        }
    }.apply {
        addShutdownHook { stop() }
    }.start(wait = true)
}
