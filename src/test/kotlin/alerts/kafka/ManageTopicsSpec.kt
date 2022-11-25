package alerts.kafka

import alerts.KafkaContainer
import alerts.autoCloseable
import alerts.install
import alerts.invoke
import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.await
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import mu.KotlinLogging

class ManageTopicsSpec : StringSpec({
  val logger = KotlinLogging.logger { }
  val kafka = install(KafkaContainer.resource())
  val admin = install { autoCloseable { Admin(AdminSettings(kafka().bootstrapServers)) } }
  val manageTopics = install { manageTopics(kafka(), logger).bind() }
  
  "manageTopics creates topics" {
    val topics = setOf(
      kafka().subscriptionTopic.name,
      kafka().eventTopic.name,
      kafka().notificationTopic.name
    )
    admin().listTopics().names().await() shouldNotContain topics
    manageTopics().initializeTopics()
    admin().listTopics().names().await() shouldContainAll topics
  }
  
  "manageTopics topics twice doesn't fail" {
    manageTopics().initializeTopics()
    manageTopics().initializeTopics()
  }
})
