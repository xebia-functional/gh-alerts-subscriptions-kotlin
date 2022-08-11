package alerts

import arrow.fx.coroutines.continuations.ResourceScope
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.TestConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlin.properties.ReadOnlyProperty

fun <A> TestConfiguration.resource(block: suspend ResourceScope.() -> A): ReadOnlyProperty<Any?, A> =
  resource(arrow.fx.coroutines.continuations.resource(block))

suspend fun <A> testApp(
  setup: Application.() -> Unit,
  test: suspend HttpClient.() -> A
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
