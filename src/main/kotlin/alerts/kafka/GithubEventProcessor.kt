package alerts.kafka

import alerts.env.Env
import alerts.persistence.SlackUserId
import com.github.avrokotlin.avro4k.AvroNamespace
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.produce
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.producer.ProducerRecord

@Serializable
@AvroNamespace("alerts.domain")
data class GithubEvent(val event: String)

@Serializable
@AvroNamespace("alerts.domain")
data class SlackNotification(val userId: SlackUserId, val event: String)

fun interface GithubEventProcessor {
  fun process(
    transform: suspend (GithubEvent) -> Flow<SlackNotification>,
  ): Flow<Unit>
}

fun githubEventProcessor(env: Env.Kafka): GithubEventProcessor = object : GithubEventProcessor {
  val consumerSettings: ReceiverSettings<Nothing, GithubEvent> =
    env.consumer(GithubEvent.serializer())
  
  val producerSettings: ProducerSettings<Nothing, SlackNotification> =
    env.producer(SlackNotification.serializer())
  
  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
