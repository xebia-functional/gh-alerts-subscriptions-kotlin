package alerts

import arrow.core.identity
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

/**
 * Utility to create a [CoroutineScope] as a [Resource].
 * It calls the correct [cancel] overload depending on the [ExitCase].
 */
context(ResourceScope)
suspend fun coroutineScope(context: CoroutineContext): CoroutineScope =
  Resource({ CoroutineScope(context) }, { scope, exitCase ->
    when (exitCase) {
      ExitCase.Completed -> scope.cancel()
      is ExitCase.Cancelled -> scope.cancel(exitCase.exception)
      is ExitCase.Failure -> scope.cancel("Resource failed, so cancelling associated scope", exitCase.failure)
    }
    scope.coroutineContext.job.join()
  }).bind()

suspend fun <A> resourceScope(
  action: suspend ResourceScope.() -> A,
): A = resource(action).use(::identity)
