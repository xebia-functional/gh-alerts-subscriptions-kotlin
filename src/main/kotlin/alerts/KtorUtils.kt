package alerts

import alerts.github.GithubErrors
import alerts.subscription.MissingRepo
import alerts.subscription.MissingSlackUser
import alerts.subscription.RepoNotFound
import alerts.subscription.SlackUserNotFound
import alerts.user.SlackUserId
import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.ensureNotNull
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

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
 * fun main(): Unit = SuspendApp {
 *   resourceScope {
 *     val dependencies = Resource({ }, { _, exitCase -> println("Closing resources") }
 *     server(Netty, port = 8080) {
 *       routing {
 *         get("ping") { call.respond("pong") }
 *       }
 *     }
 *   }
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
 *
 * https://arrow-kt.github.io/suspendapp/
 */
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
  module: Application.() -> Unit = {},
): ApplicationEngine =
  Resource({
    embeddedServer(factory, configure = configure, host = host, port = port) {
      module()
    }.apply { start() }
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

/** Small utility to turn HttpStatusCode into OutgoingContent. */
fun statusCode(statusCode: HttpStatusCode) = object : OutgoingContent.NoContent() {
  override val status: HttpStatusCode = statusCode
}

/** Small utility functions that allows to conveniently respond an `Either` where `Left == OutgoingContent`. */
context(KtorCtx)
suspend inline fun <reified A : Any> Either<OutgoingContent, A>.respond(
  code: HttpStatusCode = HttpStatusCode.OK,
): Unit =
  when (this) {
    is Either.Left -> call.respond(value)
    is Either.Right -> call.respond(code, value)
  }

typealias StatusCodeError = EffectScope<OutgoingContent>

/** Get [SlackUserId] from Query Parameters, or exit with [BadRequest] */
context(StatusCodeError)
  suspend fun KtorCtx.slackUserId(): SlackUserId =
  SlackUserId(ensureNotNull(call.request.queryParameters["slackUserId"]) { statusCode(BadRequest) })

/**
 * Rethrow [Either.Left] as [HttpStatusCode].
 * Useful to bail out with a [HttpStatusCode] without content.
 */
context(StatusCodeError)
suspend fun <E, A> Either<E, A>.or(transform: suspend (E) -> HttpStatusCode): A =
  mapLeft { statusCode(transform(it)) }.bind()

fun statusCode(statusCode: HttpStatusCode, msg: String): TextContent =
  TextContent(msg, ContentType.Application.Json, statusCode)

fun badRequest(msg: String): TextContent = statusCode(BadRequest, msg)

/**
 * Respond with [A] which must be [Serializable],
 *   or shift with [OutgoingContent], typically through [statusCode].
 *
 * This DSL can also implicitly translate some errors to [OutgoingContent].
 *  - [SlackUserNotFound] => [NotFound] + Json representation of [SlackUserNotFound]
 *  - [RepoNotFound] => [BadRequest] + Json representation of [RepoNotFound]
 *  - [GithubError] => [BadRequest] + Json representation of the underlying [HttpStatusCode].
 */
suspend inline fun <reified A : Any> KtorCtx.respond(
  code: HttpStatusCode = HttpStatusCode.OK,
  crossinline resolve: suspend context(
  MissingSlackUser,
  MissingRepo,
  GithubErrors,
  StatusCodeError
  ) (TypePlacedHolder<StatusCodeError>) -> A
): Unit = effect statusCode@{
  effect<SlackUserNotFound, A> slackUser@{
    effect<RepoNotFound, A> missingRepo@{
      effect {
        // We need to manually wire the contexts, because the Kotlin Compiler doesn't properly understand this yet.
        resolve(this@slackUser, this@missingRepo, this, this@statusCode, TypePlacedHolder)
      } catch { shift(badRequest(it.asJson())) }
    } catch { shift(badRequest(it.toJson())) }
  } catch { shift(statusCode(NotFound, it.toJson())) }
}.fold({ call.respond(it) }) { call.respond(code, it) }

/**
 * This is a temporary hack to make `context` based lambdas work correctly.
 * See https://youtrack.jetbrains.com/issue/KT-51243
 */
sealed interface TypePlacedHolder<out A> {
  companion object : TypePlacedHolder<Nothing>
}
