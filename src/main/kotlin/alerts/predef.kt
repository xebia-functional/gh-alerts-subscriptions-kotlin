package alerts

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

/**
 * Ktor [ApplicationEngine] as a [Resource].
 * This [Resource] will gracefully shut down the server
 * When we need to shut down a Ktor service we need to properly take into account a _grace_ period where we still handle
 * requests instead of immediately cancelling any in-flight requests.
 *
 * We also need to add a _prewait_ for allowing any Ingress or any Load Balancers to de-register our service.
 * https://philpearl.github.io/post/k8s_ingress/
 *
 * @param preWait a duration to wait before beginning the stop process. During this time, requests will continue
 * to be accepted. This setting is useful to allow time for the container to be removed from the load balancer.
 * This is disabled when `io.ktor.development=true`.
 *
 * @param grace a duration during which already inflight requests are allowed to continue before the
 * shutdown process begins.
 *
 * @param timeout a duration after which the server will be forceably shutdown.
 *
 * ```kotlin
 * fun main(): Unit = cancelOnShutdown {
 *   resource {
 *     val engine = server(Netty, port = 8080).bind()
 *     val dependencies = Resource({ }, { _, exitCase -> println("Closing resources") }
 *     engine.application.routing {
 *       get("ping") { call.respond("pong") }
 *     }
 *   }.use { awaitCancellation() }
 * }
 *
 * // Server Start
 * // JVM SIGTERM - event
 * // Shutting down HTTP server...
 * // ... graceful shutdown ktor engine
 * // HTTP server shutdown!
 * // Closing resources
 * // exit (0)
 * ```
 */
@Suppress("LongParameterList")
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> server(
  factory: ApplicationEngineFactory<TEngine, TConfiguration>,
  port: Int = 80,
  host: String = "0.0.0.0",
  configure: TConfiguration.() -> Unit = {},
  preWait: Duration = 30.seconds,
  grace: Duration = 1.seconds,
  timeout: Duration = 5.seconds,
  module: suspend Application.() -> Unit = {},
): Resource<ApplicationEngine> =
  Resource({
    embeddedServer(factory, host = host, port = port, configure = configure) {
    }.apply {
      module(application)
      start()
    }
  }, { engine, _ ->
    if (!engine.environment.developmentMode) {
      engine.environment.log.info(
        "prewait delay of ${preWait.inWholeMilliseconds}ms, turn it off using io.ktor.development=true"
      )
      delay(preWait.inWholeMilliseconds)
    }
    engine.environment.log.info("Shutting down HTTP server...")
    engine.stop(grace.inWholeMilliseconds, timeout.inWholeMilliseconds)
    engine.environment.log.info("HTTP server shutdown!")
  })

/**
 * Utility to create a [CoroutineScope] as a [Resource].
 * It calls the correct [cancel] overload depending on the [ExitCase].
 */
fun Resource.Companion.coroutineScope(context: CoroutineContext): Resource<CoroutineScope> =
  Resource({ CoroutineScope(context) }, { scope, exitCase ->
    when (exitCase) {
      ExitCase.Completed -> scope.cancel()
      is ExitCase.Cancelled -> scope.cancel(exitCase.exception)
      is ExitCase.Failure -> scope.cancel("Resource failed, so cancelling associated scope", exitCase.failure)
    }
    scope.coroutineContext.job.join()
  })
