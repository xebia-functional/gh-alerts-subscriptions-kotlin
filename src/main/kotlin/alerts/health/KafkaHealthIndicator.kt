package alerts.health

import alerts.env.Kafka
import arrow.core.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component

@Component
class KafkaHealthIndicator(
    private val kafka: Kafka,
    private val kafkaAdmin: KafkaAdmin,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : HealthIndicator {

    override fun health(): Health =
        runBlocking {
            doHealthCheck().fold(
                { Health.outOfService().withException(it).build() },
                { Health.up().build() }
            )
        }

    suspend fun doHealthCheck(): Either<Throwable, Unit> =
        Either.catch {
            withContext(dispatcher) {
                kafkaAdmin.describeTopics(
                    kafka.subscription.name,
                    kafka.event.name,
                    kafka.notification.name
                ).let { }
            }
        }.mapLeft { it }
}
