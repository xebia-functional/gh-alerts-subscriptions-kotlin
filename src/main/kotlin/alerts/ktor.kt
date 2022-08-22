package alerts

import alerts.https.client.GithubErrors
import alerts.https.client.GithubError
import alerts.persistence.SlackUserId
import alerts.service.MissingRepo
import alerts.service.MissingSlackUser
import alerts.service.RepoNotFound
import alerts.service.SlackUserNotFound
import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.ensureNotNull
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
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

/** Turn [HttpStatusCode] into [OutgoingContent]. */
fun statusCode(statusCode: HttpStatusCode): OutgoingContent = object : OutgoingContent.NoContent() {
  override val status: HttpStatusCode = statusCode
}

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
    } catch { shift(badRequest(it.asJson())) }
  } catch { shift(statusCode(NotFound, it.asJson())) }
}.fold({ call.respond(it) }) { call.respond(code, it) }

/** https://arrow-kt.github.io/suspendapp/ */
context(ResourceScope)
@Suppress("LongParameterList")
suspend fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> server(
  factory: ApplicationEngineFactory<TEngine, TConfiguration>,
  port: Int = 80,
  host: String = "0.0.0.0",
  preWait: Duration = 30.seconds,
  grace: Duration = 1.seconds,
  timeout: Duration = 5.seconds,
): ApplicationEngine =
  Resource({
    embeddedServer(factory, host = host, port = port) {}.apply { start() }
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

/**
 * This is a temporary hack to make `context` based lambdas work correctly.
 * See https://youtrack.jetbrains.com/issue/KT-51243
 */
sealed interface TypePlacedHolder<out A> {
  companion object : TypePlacedHolder<Nothing>
}
