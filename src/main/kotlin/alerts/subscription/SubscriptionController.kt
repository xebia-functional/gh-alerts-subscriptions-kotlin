package alerts.subscription

import alerts.user.SlackUserId
import arrow.core.merge
import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@Controller
class SubscriptionController(
  private val service: SubscriptionService,
  private val clock: Clock,
  private val timeZone: TimeZone
) {
  @GetMapping("/subscription")
  suspend fun get(
    @RequestParam("slackUserId") slackUserId: String
  ): ResponseEntity<Subscriptions> =
    either {
      val subscriptions = service.findAll(SlackUserId(slackUserId))
        .mapLeft { ResponseEntity<Subscriptions>(BAD_REQUEST) }
        .bind()

      ResponseEntity.ok(Subscriptions(subscriptions))
    }.merge()

  @PostMapping("/subscription")
  suspend fun post(
    @RequestParam("slackUserId") slackUserId: String,
    @RequestBody repository: Repository
  ): ResponseEntity<*> =
    service.subscribe(SlackUserId(slackUserId), Subscription(repository, clock.now().toLocalDateTime(timeZone)))
      .fold(
        { error -> ResponseEntity.badRequest().body(error.toJson()) },
        { ResponseEntity.status(HttpStatus.CREATED).body(it) }
      )

  @DeleteMapping("/subscription")
  suspend fun delete(
    @RequestParam("slackUserId") slackUserId: String,
    @RequestBody repository: Repository
  ): ResponseEntity<Nothing> =
    service.unsubscribe(SlackUserId(slackUserId), repository)
      .fold(
        { ResponseEntity(HttpStatus.NOT_FOUND) },
        { ResponseEntity(HttpStatus.NO_CONTENT) }
      )
}
