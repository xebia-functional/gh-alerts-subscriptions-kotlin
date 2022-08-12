package alerts

import alerts.persistence.SlackUserId
import arrow.core.identity
import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.ensureNotNull
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

typealias StatusCodeError = EffectScope<OutgoingContent>

/** Get slackUserId, or return BadRequest */
context(StatusCodeError)
suspend fun KtorCtx.slackUserId(): SlackUserId =
  SlackUserId(ensureNotNull(call.request.queryParameters["slackUserId"]) { statusCode(BadRequest) })

/**
 * Rethrow `Left` as `HttpStatusCode`.
 * Useful to bail out with a HttpStatusCode with no content
 */
context(StatusCodeError)
suspend fun <A> Either<*, A>.or(code: HttpStatusCode): A =
  mapLeft { statusCode(code) }.bind()

/** Turn HttpStatusCode into OutgoingContent. */
fun statusCode(statusCode: HttpStatusCode) = object : OutgoingContent.NoContent() {
  override val status: HttpStatusCode = statusCode
}

/** Respond with `A` which must be `@Serializable` or shift with OutgoingContent. */
suspend inline fun <reified A : Any> KtorCtx.respond(
  code: HttpStatusCode = HttpStatusCode.OK,
  crossinline resolve: suspend StatusCodeError.() -> A,
): Unit = effect(resolve).fold({ call.respond(it) }) { call.respond(code, it) }

/** https://arrow-kt.github.io/suspendapp/ */
context(ResourceScope)
@Suppress("LongParameterList")
suspend fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> server(
  factory: ApplicationEngineFactory<TEngine, TConfiguration>,
  port: Int = 80,
  host: String = "0.0.0.0",
  configure: TConfiguration.() -> Unit = {},
  preWait: Duration = 30.seconds,
  grace: Duration = 1.seconds,
  timeout: Duration = 5.seconds,
  module: suspend Application.() -> Unit = {},
): ApplicationEngine =
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
  }).bind()
