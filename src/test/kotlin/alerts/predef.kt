package alerts

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceDSL
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.continuations.resource
import io.kotest.assertions.arrow.fx.coroutines.extension
import io.kotest.core.extensions.LazyMaterialized
import io.kotest.core.spec.Spec
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.testcontainers.lifecycle.Startable

@ResourceDSL
suspend fun <A : AutoCloseable> ResourceScope.autoCloseable(autoCloseable: suspend () -> A): A =
  install({ autoCloseable() }) { closeable, _ ->
    withContext(Dispatchers.IO) {
      closeable.close()
    }
  }

@ResourceDSL
suspend fun <A : Startable> ResourceScope.startable(startable: suspend () -> A): A =
  install({
    withContext(Dispatchers.IO) {
      startable().also { runInterruptible(block = it::start) }
    }
  }) { closeable, _ ->
    withContext(Dispatchers.IO) {
      closeable.close()
    }
  }

suspend operator fun <A> LazyMaterialized<A>.invoke(): A = get()

fun <MATERIALIZED> Spec.install(resource: Resource<MATERIALIZED>): LazyMaterialized<MATERIALIZED> {
  val ext = resource.extension()
  extensions(ext)
  return ext.mount { }
}

fun <MATERIALIZED> Spec.install(
  acquire: suspend ResourceScope.() -> MATERIALIZED,
): LazyMaterialized<MATERIALIZED> {
  val ext = resource(acquire).extension()
  extensions(ext)
  return ext.mount { }
}

suspend fun <A> testApp(
  setup: Application.() -> Unit,
  test: suspend HttpClient.() -> A,
): A {
  val result = CompletableDeferred<A>()
  testApplication {
    application {
      configure()
      setup()
    }
    createClient {
      install(ContentNegotiation) { json() }
      expectSuccess = false
    }.use { result.complete(test(it)) }
  }
  return result.await()
}
