package alerts.github

import alerts.IntegrationTestBase
import alerts.env.Kafka
import alerts.env.receiverOptions
import alerts.env.senderOptions
import alerts.user.SlackUserId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import reactor.kafka.sender.SenderOptions
import reactor.kafka.sender.SenderResult

class GithubEventProcessorSpec(
  private val kafka: Kafka,
  private val processor: EnvGithubEventProcessor
) : IntegrationTestBase({

  val eventProducerOptions = with(kafka) {
    senderOptions(producerProperties(), GithubEvent.serializer())
  }

  val notificationOptions = with(kafka) {
    receiverOptions(notification.name, consumerProperties(), SlackNotification.serializer())
  }
  
  "All received events are processed and sent to the notification topic" {
    val events = listOf(
      GithubEvent("arrow-kt/arrow"),
      GithubEvent("arrow-kt/arrow-analysis")
    )

    events.map { ProducerRecord<Nothing, GithubEvent>(kafka.event.name, it) }
      .asFlow()
      .produce(eventProducerOptions)
      .collect()
    
    processor.process { event ->
      flowOf(SlackNotification(SlackUserId("1"), event.event))
    }.take(events.size).collect()

    ReactiveKafkaConsumerTemplate(notificationOptions)
      .receiveAutoAck()
      .asFlow()
      .take(events.size)
      .map { it.value() }
      .toList() shouldBe events.map { SlackNotification(SlackUserId("1"), it.event) }
  }
  
  "Single received events can produce many events" {
    val events = listOf(GithubEvent("arrow-kt/arrow"))
    events.map { ProducerRecord<Nothing, GithubEvent>(kafka.event.name, it) }
      .asFlow()
      .produce(eventProducerOptions)
      .collect()
    
    processor.process { event ->
      flowOf(
        SlackNotification(SlackUserId("1"), event.event),
        SlackNotification(SlackUserId("2"), event.event)
      )
    }.take(events.size * 2).collect()

    ReactiveKafkaConsumerTemplate(notificationOptions)
      .receiveAutoAck()
      .asFlow()
      .take(events.size * 2)
      .map { it.value() }
      .toList() shouldBe events
      .flatMap {
        listOf(
          SlackNotification(SlackUserId("1"), it.event),
          SlackNotification(SlackUserId("2"), it.event)
        )
      }
  }
})

private suspend fun <A, B> Flow<ProducerRecord<A, B>>.produce(
  options: SenderOptions<A, B>,
): Flow<SenderResult<Void>> =
  ReactiveKafkaProducerTemplate(options).let { producer ->
    this@produce.map { record -> producer.send(record).awaitSingle() }
  }
