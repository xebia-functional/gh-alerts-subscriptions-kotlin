package alerts.kafka

import alerts.KafkaContainer
import alerts.persistence.SlackUserId
import io.github.nomisRev.kafka.commitBatchWithin
import io.github.nomisRev.kafka.component1
import io.github.nomisRev.kafka.component2
import io.github.nomisRev.kafka.kafkaConsumer
import io.github.nomisRev.kafka.produce
import io.github.nomisRev.kafka.subscribeTo
import io.kotest.assertions.arrow.fx.coroutines.resource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.time.Duration.Companion.seconds

class GithubEventProcessorSpec : StringSpec({
  val kafka by resource(KafkaContainer.resource())
  val processor by lazy { githubEventProcessor(kafka) }
  val eventProducerSetting by lazy { kafka.producer(GithubEvent.serializer()) }
  val notificationSettings by lazy { kafka.consumer(SlackNotification.serializer()) }
  
  "All received events are processed and send to the event topic" {
    val events = listOf(
      GithubEvent("arrow-kt/arrow"),
      GithubEvent("arrow-kt/arrow-analysis")
    )
    events.map { ProducerRecord(kafka.eventTopic.name, UnitKey(), it) }
      .asFlow()
      .produce(eventProducerSetting)
      .collect()
    
    processor.process { event ->
      flowOf(SlackNotification(SlackUserId("1"), event.event))
    }.take(1).collect()
    
    val buffer = mutableListOf<SlackNotification>()
    
    kafkaConsumer(notificationSettings)
      .subscribeTo(kafka.notificationTopic.name)
      .take(2)
      .onEach { (_, value) ->
        buffer.add(value)
      }.commitBatchWithin(notificationSettings, 1, 15.seconds)
      .collect()
    
    buffer shouldBe events.map { SlackNotification(SlackUserId("1"), it.event) }
  }
  
  "Single received events can produce many events" {
    val events = listOf(GithubEvent("arrow-kt/arrow"))
    events.map { ProducerRecord(kafka.eventTopic.name, UnitKey(), it) }
      .asFlow()
      .produce(eventProducerSetting)
      .collect()
    
    processor.process { event ->
      flowOf(
        SlackNotification(SlackUserId("1"), event.event),
        SlackNotification(SlackUserId("2"), event.event)
      )
    }.take(1).collect()
    
    val buffer = mutableListOf<SlackNotification>()
    
    kafkaConsumer(notificationSettings)
      .subscribeTo(kafka.notificationTopic.name)
      .take(2)
      .onEach { (_, value) ->
        buffer.add(value)
      }.commitBatchWithin(notificationSettings, 1, 15.seconds)
      .collect()
    
    buffer shouldBe events.flatMap {
      listOf(SlackNotification(SlackUserId("1"), it.event), SlackNotification(SlackUserId("2"), it.event))
    }
  }
})
