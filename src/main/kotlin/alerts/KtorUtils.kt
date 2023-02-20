package alerts

import arrow.core.Either
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.ResourceScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

/**
 * Utility to create a [CoroutineScope] as a [Resource].
 * It calls the correct [cancel] overload depending on the [ExitCase].
 */
suspend fun ResourceScope.coroutineScope(context: CoroutineContext): CoroutineScope =
  install({ CoroutineScope(context) }, { scope, exitCase ->
    when (exitCase) {
      ExitCase.Completed -> scope.cancel()
      is ExitCase.Cancelled -> scope.cancel(exitCase.exception)
      is ExitCase.Failure -> scope.cancel("Resource failed, so cancelling associated scope", exitCase.failure)
    }
    scope.coroutineContext.job.join()
  })

/** Small utility to turn HttpStatusCode into OutgoingContent. */
fun statusCode(statusCode: HttpStatusCode) = object : OutgoingContent.NoContent() {
  override val status: HttpStatusCode = statusCode
}

fun badRequest(content: String, contentType: ContentType = ContentType.Text.Plain) =
  TextContent(content, contentType, HttpStatusCode.BadRequest)

/** Small utility functions that allows to conveniently respond an `Either` where `Left == OutgoingContent`. */
context(PipelineContext<Unit, ApplicationCall>)
suspend inline fun <reified A : Any> Either<OutgoingContent, A>.respond(
  code: HttpStatusCode = HttpStatusCode.OK,
): Unit =
  when (this) {
    is Either.Left -> call.respond(value)
    is Either.Right -> call.respond(code, value)
  }
