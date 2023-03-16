package alerts

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

object TestMetrics {
  val slackUsersCounter: Counter =
    Counter.build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(CollectorRegistry.defaultRegistry)
}
