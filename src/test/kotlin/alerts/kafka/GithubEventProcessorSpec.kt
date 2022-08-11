package alerts.kafka

import alerts.KafkaContainer
import alerts.persistence.SlackUserId
import alerts.resource
import io.github.nomisRev.kafka.produce
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.apache.kafka.clients.producer.ProducerRecord

class GithubEventProcessorSpec : StringSpec({
  val kafka by resource { KafkaContainer() }
  val processor by lazy { GithubEventProcessor(kafka) }
  val eventProducerSetting by lazy { kafka.producer(GithubEvent.serializer()) }
  val notificationSettings by lazy { kafka.consumer(SlackNotification.serializer()) }
  
  "All received events are processed and sent to the notification topic" {
    val events = listOf(
      GithubEvent("arrow-kt/arrow"),
      GithubEvent("arrow-kt/arrow-analysis")
    )
    events.map { ProducerRecord<Nothing, GithubEvent>(kafka.eventTopic.name, it) }
      .asFlow()
      .produce(eventProducerSetting)
      .collect()
    
    processor.process { event ->
      flowOf(SlackNotification(SlackUserId("1"), event.event))
    }.take(events.size).collect()
    
    KafkaReceiver(notificationSettings)
      .receiveAutoAck(kafka.notificationTopic.name)
      .flattenConcat()
      .take(events.size)
      .map { it.value() }
      .toList() shouldBe events.map { SlackNotification(SlackUserId("1"), it.event) }
  }
  
  "Single received events can produce many events" {
    val events = listOf(GithubEvent("arrow-kt/arrow"))
    events.map { ProducerRecord<Nothing, GithubEvent>(kafka.eventTopic.name, it) }
      .asFlow()
      .produce(eventProducerSetting)
      .collect()
    
    processor.process { event ->
      flowOf(
        SlackNotification(SlackUserId("1"), event.event),
        SlackNotification(SlackUserId("2"), event.event)
      )
    }.take(events.size * 2).collect()
    
    KafkaReceiver(notificationSettings)
      .receiveAutoAck(kafka.notificationTopic.name)
      .flattenConcat()
      .take(events.size * 2)
      .map { it.value() }
      .toList() shouldBe events
      .flatMap { listOf(SlackNotification(SlackUserId("1"), it.event), SlackNotification(SlackUserId("2"), it.event)) }
  }
})
