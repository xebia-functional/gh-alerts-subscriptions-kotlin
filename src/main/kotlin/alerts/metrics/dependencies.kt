package alerts.metrics

import arrow.fx.coroutines.Resource
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

val metricsRegistry: Resource<PrometheusMeterRegistry> =
  Resource({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _ -> p.close() }

fun slackUsersCounter(meterRegistry: PrometheusMeterRegistry): Counter =
  Counter
    .build()
    .name("slack_users_counter")
    .help("Number of Slack users registered with our service")
    .register(meterRegistry.prometheusRegistry)
