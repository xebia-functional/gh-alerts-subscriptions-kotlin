package alerts.github

import alerts.env.Env
import arrow.core.Either
import arrow.core.continuations.either
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.fromAutoCloseable
import arrow.fx.coroutines.retry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource as KtorRes
import kotlinx.serialization.Serializable
import mu.KLogger
import mu.KotlinLogging
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

fun interface GithubClient {
  suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean>
}

context(ResourceScope)
suspend fun GithubClient(
  config: Env.Github,
  retryPolicy: Schedule<Throwable, Unit> = defaultPolicy
): GithubClient {
  val client = Resource.fromAutoCloseable {
    HttpClient {
      install(HttpCache)
      install(DefaultRequest) { url(config.uri) }
    }
  }.bind()
  val logger = KotlinLogging.logger { }
  return DefaultGithubClient(config, retryPolicy, client, logger)
}

private const val DEFAULT_RETRY_COUNT: Int = 3

@OptIn(ExperimentalTime::class)
private val defaultPolicy: Schedule<Throwable, Unit> =
  Schedule.recurs<Throwable>(DEFAULT_RETRY_COUNT)
    .and(Schedule.exponential(1.seconds))
    .void()

private class DefaultGithubClient(
  private val config: Env.Github,
  private val retryPolicy: Schedule<Throwable, Unit>,
  private val httpClient: HttpClient,
  private val logger: KLogger,
) : GithubClient {
  @Serializable
  @KtorRes("/repos/{owner}/{repo}")
  class Repo(val owner: String, val repo: String)

  override suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean> = either {
    retryPolicy.retry {
      val response = httpClient.get(Repo(owner, name)) {
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
