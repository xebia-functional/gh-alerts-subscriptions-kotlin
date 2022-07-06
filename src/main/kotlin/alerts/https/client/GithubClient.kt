package alerts.https.client

import alerts.env.Env
import arrow.core.Either
import arrow.core.continuations.either
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.fromAutoCloseable
import arrow.fx.coroutines.retry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import mu.KLogger
import mu.KotlinLogging
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@JvmInline
value class GithubError(val statusCode: HttpStatusCode)

interface GithubClient {
  suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean>
  
  companion object {
    fun resource(
      config: Env.Github,
      retryPolicy: Schedule<Throwable, Unit> = defaultPolicy,
    ): Resource<GithubClient> = resource {
      val client = Resource.fromAutoCloseable { HttpClient() }.bind()
      val logger = KotlinLogging.logger { }
      DefaultGithubClient(config, retryPolicy, client, logger)
    }
    
    @OptIn(ExperimentalTime::class)
    private val defaultPolicy: Schedule<Throwable, Unit> =
      Schedule.recurs<Throwable>(3)
        .and(Schedule.exponential(1.seconds))
        .void()
  }
}

private class DefaultGithubClient(
  private val config: Env.Github,
  private val retryPolicy: Schedule<Throwable, Unit>,
  private val httpClient: HttpClient,
  private val logger: KLogger,
) : GithubClient {
  override suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean> = either {
    retryPolicy.retry {
      val response = httpClient.get("${config.uri}/repos/$owner/$name") {
        config.token?.let { token -> headers.append("Authorization", "Bearer $token") }
        expectSuccess = false
      }
      when (response.status) {
        HttpStatusCode.OK -> true
        HttpStatusCode.NotModified -> true
        HttpStatusCode.NotFound -> false
        else -> {
          logger.info { "GitHub call failed with status: ${response.status.description}" }
          shift(GithubError(response.status))
        }
      }
    }
  }
}
