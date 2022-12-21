package alerts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
  @Suppress("SpreadOperator")
  runApplication<CoroutinesApplication>(*args)
}

@SpringBootApplication
@ConfigurationPropertiesScan
class CoroutinesApplication
