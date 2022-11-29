/**
 * This file only contains code that was written for Arrow 2.0.0, and is going to be backported to Arrow 1.0.0
 * This provides us with an experimentation ground for the new APIs, to see their viability.
 */
package alerts

import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.identity
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalTypeInference

context(EffectScope<E2>)
@OptIn(ExperimentalTypeInference::class)
suspend infix fun <E, E2, A> Effect<E, A>.catch(@BuilderInference resolve: suspend EffectScope<E2>.(E) -> A): A =
  effect { fold({ resolve(it) }, { it }) }.bind()

@OptIn(ExperimentalTypeInference::class)
infix fun <E, A> Effect<E, A>.attempt(
  @BuilderInference recover: suspend EffectScope<E>.(Throwable) -> A,
): Effect<E, A> = effect {
  fold(
    { recover(it) },
    { shift(it) },
    { it }
  )
}

@OptIn(ExperimentalTypeInference::class)
@JvmName("attemptOrThrow")
public inline infix fun <reified T : Throwable, E, A> Effect<E, A>.attempt(
  @BuilderInference crossinline recover: suspend EffectScope<E>.(T) -> A,
): Effect<E, A> = attempt { e -> if (e is T) recover(e) else throw e }

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
