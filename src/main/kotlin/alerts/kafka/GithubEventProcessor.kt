package alerts.kafka

import alerts.env.Env
import alerts.persistence.SlackUserId
import com.github.avrokotlin.avro4k.AvroNamespace
import io.github.nomisRev.kafka.ConsumerSettings
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.commitBatchWithin
import io.github.nomisRev.kafka.kafkaConsumer
import io.github.nomisRev.kafka.produce
import io.github.nomisRev.kafka.subscribeTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

fun githubEventProcessor(
  env: Env.Kafka,
  commitBatchSize: Int = 500,
  commitBatchTimeout: Duration = 15.seconds,
): GithubEventProcessor = object : GithubEventProcessor {
  val consumerSettings: ConsumerSettings<UnitKey, GithubEvent> =
    env.consumer(GithubEvent.serializer())
  
  val producerSettings: ProducerSettings<UnitKey, SlackNotification> =
    env.producer(SlackNotification.serializer())
  
  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  override fun process(
    transform: suspend (GithubEvent) -> Flow<SlackNotification>,
  ): Flow<Unit> =
    kafkaConsumer(consumerSettings)
      .subscribeTo(env.eventTopic.name)
      .flatMapConcat { record ->
        transform(record.value())
          .map { ProducerRecord(env.notificationTopic.name, UnitKey(), it) }
          .produce(producerSettings)
          .map { record }
      }.commitBatchWithin(consumerSettings, commitBatchSize, commitBatchTimeout)
}
