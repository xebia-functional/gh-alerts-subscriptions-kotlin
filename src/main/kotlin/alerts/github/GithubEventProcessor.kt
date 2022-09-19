package alerts.github

import alerts.env.Env
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.produce
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.apache.kafka.clients.producer.ProducerRecord

fun interface GithubEventProcessor {
  fun process(
    transform: suspend (GithubEvent) -> Flow<SlackNotification>,
  ): Flow<Unit>
}

class EnvGithubEventProcessor(private val env: Env.Kafka): GithubEventProcessor {
    private val consumerSettings: ReceiverSettings<Nothing, GithubEvent> =
      env.consumer(GithubEvent.serializer())
    
    private val producerSettings: ProducerSettings<Nothing, SlackNotification> =
      env.producer(SlackNotification.serializer())
    
    @OptIn(FlowPreview::class)
    override fun process(
      transform: suspend (GithubEvent) -> Flow<SlackNotification>,
    ): Flow<Unit> =
      KafkaReceiver(consumerSettings)
        .receive(env.eventTopic.name)
        .flatMapConcat { record ->
          transform(record.value())
            .map { ProducerRecord<Nothing, SlackNotification>(env.notificationTopic.name, it) }
            .produce(producerSettings)
            .map { }
            .onCompletion { record.offset.acknowledge() }
        }
  }
