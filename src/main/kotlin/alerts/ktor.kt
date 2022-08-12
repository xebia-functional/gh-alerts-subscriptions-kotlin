package alerts

import alerts.https.client.GithubError
import alerts.kafka.GithubEvent
import alerts.persistence.SlackUserId
import alerts.service.MissingRepo
import alerts.service.MissingSlackUser
import alerts.service.RepoNotFound
import alerts.service.SlackUserNotFound
import arrow.core.identity
import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.ensureNotNull
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
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
import kotlin.experimental.ExperimentalTypeInference

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
suspend fun <A> Either<*, A>.or(code: HttpStatusCode): A =
  mapLeft { statusCode(code) }.bind()

/** Turn [HttpStatusCode] into [OutgoingContent]. */
fun statusCode(statusCode: HttpStatusCode): OutgoingContent = object : OutgoingContent.NoContent() {
  override val status: HttpStatusCode = statusCode
}

/**
 * Respond with [A] which must be [Serializable],
 *   or shift with [OutgoingContent], typically through [statusCode].
 *
 * This DSL can also implicitly translate some errors to [OutgoingContent].
 *  - [SlackUserNotFound] => [NotFound]
 *  - [RepoNotFound] => [BadRequest]
 *  - [GithubError] => [BadRequest]
 */
suspend inline fun <reified A : Any> KtorCtx.respond(
  code: HttpStatusCode = HttpStatusCode.OK,
  crossinline resolve: suspend context(
    MissingSlackUser,
    MissingRepo,
    EffectScope<GithubError>,
    StatusCodeError
  ) (TypePlacedHolder<StatusCodeError>) -> A
): Unit = effect statusCode@{
  effect<SlackUserNotFound, A> slackUser@{
    effect<RepoNotFound, A> missingRepo@{
      effect {
        // We need to manually wire the contexts, because the Kotlin Compiler doesn't properly understand this yet.
        resolve(this@slackUser, this@missingRepo, this, this@statusCode, TypePlacedHolder)
      } catch { shift(statusCode(BadRequest)) }
    } catch { shift(statusCode(BadRequest)) }
  } catch { shift(statusCode(NotFound)) }
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

// TODO copied from Arrow 2.0 branch, backport to 1.x.x
context(EffectScope<E2>)
@OptIn(ExperimentalTypeInference::class)
suspend infix fun <E, E2, A> Effect<E, A>.catch(@BuilderInference resolve: suspend EffectScope<E2>.(E) -> A): A =
  effect { fold({ resolve(it) }, { it }) }.bind()
