package alerts.metrics

import arrow.fx.coroutines.ResourceScope
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
  install({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _ -> p.close() }

fun slackUsersCounter(meterRegistry: PrometheusMeterRegistry): Counter =
  Counter
    .build()
    .name("slack_users_counter")
    .help("Number of Slack users registered with our service")
    .register(meterRegistry.prometheusRegistry)
