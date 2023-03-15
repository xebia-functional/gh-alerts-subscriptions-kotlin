package alerts

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object TestMetrics {
  val slackUsersCounter: Counter =
    Counter.build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(CollectorRegistry.defaultRegistry)
}

fun LocalDateTime.Companion.now(): LocalDateTime =
  Clock.System.now().toLocalDateTime(TimeZone.UTC)
