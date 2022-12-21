package alerts.metrics

import io.micrometer.prometheus.PrometheusMeterRegistry
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class MetricsController(private val metrics: PrometheusMeterRegistry) {
  @GetMapping("/metrics")
  fun metrics(): String =
    metrics.scrape()
}
