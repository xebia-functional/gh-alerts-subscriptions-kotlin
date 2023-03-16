package alerts.github

import alerts.env.Github
import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.resilience.Schedule
import arrow.fx.resilience.retry
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.seconds

fun interface GithubClient {
  suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean>
}

private const val DEFAULT_GITHUB_RETRY_COUNT = 3

private val DEFAULT_GITHUB_RETRY_SCHEDULE: Schedule<Throwable, Unit> =
  Schedule.recurs<Throwable>(DEFAULT_GITHUB_RETRY_COUNT)
    .and(Schedule.exponential(1.seconds))
    .void()

@Component
class DefaultGithubClient(
  private val config: Github,
  private val httpClient: WebClient,
  private val retryPolicy: Schedule<Throwable, Unit> = DEFAULT_GITHUB_RETRY_SCHEDULE,
) : GithubClient {
  val logger = KotlinLogging.logger { }

  override suspend fun repositoryExists(owner: String, name: String): Either<GithubError, Boolean> = either {
    retryPolicy.retry {
      val response = httpClient.get().apply {
        uri { builder ->
          builder.path("/repos/{owner}/{name}").build(owner, name)
        }
        config.token?.let { token -> header("Authorization", "Bearer $token") }
      }.retrieve()
        .onStatus({ true }, { Mono.empty() })
        .toEntity(String::class.java)
        .awaitSingle()

      when (response.statusCode) {
        HttpStatus.OK -> true
        HttpStatus.NOT_MODIFIED -> true
        HttpStatus.NOT_FOUND -> false
        else -> {
          logger.info { "GitHub call failed with status: ${response.statusCode}" }
          raise(GithubError(response.statusCode))
        }
      }
    }
  }
}
