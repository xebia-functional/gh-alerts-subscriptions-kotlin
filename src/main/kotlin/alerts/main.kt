package alerts

import alerts.env.Dependencies
import alerts.env.Env
import alerts.routes.healthRoute
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.guaranteeCase
import arrow.fx.coroutines.parZip
import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun main(): Unit = cancelOnShutdown {
  val env = Env()
  resource {
    val engine = server(Netty, port = env.http.port, host = env.http.host).bind()
    val dependencies = Dependencies.resource(env).bind()
    engine.application.routing {
      healthRoute()
    }
    dependencies.notifications.processor().bind()
  }.use { awaitCancellation() }
}
