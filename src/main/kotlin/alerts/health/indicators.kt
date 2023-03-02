package alerts.health

import alerts.env.Kafka
import arrow.core.Either
import org.flywaydb.core.Flyway
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component

abstract class CustomHealthIndicator(
    private val validate: () -> Unit
) : HealthIndicator {

    override fun health(): Health =
        Either.catch(validate)
            .fold(
                { Health.outOfService().withException(it).build() },
                { Health.up().build() }
            )
}

@Component
class KafkaHealthIndicator(
    private val kafka: Kafka,
    private val kafkaAdmin: KafkaAdmin
) : CustomHealthIndicator({
    kafkaAdmin.describeTopics(
        kafka.subscription.name,
        kafka.event.name,
        kafka.notification.name
    )
})

@Component
class FlywayHealthIndicator(
    private val flyway: Flyway
) : CustomHealthIndicator({
    flyway.validate()
})
