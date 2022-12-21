package alerts.github

import alerts.env.Kafka
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Component

fun interface GithubEventProcessor {
  fun process(
    transform: suspend (GithubEvent) -> Flow<SlackNotification>,
  ): Flow<Unit>
}

@Component
class EnvGithubEventProcessor(
  private val kafka: Kafka,
  private val receiver: ReactiveKafkaConsumerTemplate<Nothing, GithubEvent>,
  @Qualifier("notification") private val producer: ReactiveKafkaProducerTemplate<Nothing, SlackNotification>
) : GithubEventProcessor {

  @OptIn(FlowPreview::class)
  override fun process(
    transform: suspend (GithubEvent) -> Flow<SlackNotification>,
  ): Flow<Unit> =
    receiver
      .receive()
      .asFlow()
      .flatMapConcat { record ->
        transform(record.value())
          .map {
            producer.send(ProducerRecord(kafka.notification.name, it)).awaitSingle()
            Unit
          }.onCompletion { record.receiverOffset().acknowledge() }
      }
}
