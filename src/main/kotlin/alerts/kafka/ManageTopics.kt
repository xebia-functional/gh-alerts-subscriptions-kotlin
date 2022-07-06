package alerts.kafka

import alerts.domain.GithubEvent
import alerts.domain.SlackNotification
import alerts.domain.SubscriptionEvent
import alerts.domain.SubscriptionKey
import alerts.env.Env
import alerts.persistence.catch
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromAutoCloseable
import com.github.avrokotlin.avro4k.Avro
import io.github.nomisRev.kafka.Admin
import io.github.nomisRev.kafka.AdminSettings
import io.github.nomisRev.kafka.createTopic
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import mu.KLogger
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException

interface ManageTopics {
  suspend fun initializeTopics(): Unit
}

fun manageTopics(
  config: Env.Kafka,
  logger: KLogger,
  registryClientCacheSize: Int = 100
): Resource<ManageTopics> = resource {
  val admin = Resource.fromAutoCloseable { Admin(AdminSettings(config.bootstrapServers)) }.bind()
  val registry = CachedSchemaRegistryClient(config.schemaRegistryUrl, registryClientCacheSize)
  DefaultManageTopic(config, admin, registry, logger)
}

private class DefaultManageTopic(
  val config: Env.Kafka,
  val admin: Admin,
  val registryClient: SchemaRegistryClient,
  val logger: KLogger,
) : ManageTopics {

  override suspend fun initializeTopics(): Unit = withContext(Dispatchers.IO) {
    initializeTopic(config.subscriptionTopic, SubscriptionKey.serializer(), SubscriptionEvent.serializer())
    initializeTopic(config.eventTopic, Unit.serializer(), GithubEvent.serializer())
    initializeTopic(config.notificationTopic, Unit.serializer(), SlackNotification.serializer())
  }

  private fun Env.Kafka.Topic.toNewTopic(): NewTopic = NewTopic(name, numPartitions, replicationFactor)

  private suspend fun <K, V> initializeTopic(
    topic: Env.Kafka.Topic,
    keyS: SerializationStrategy<K>,
    valueS: SerializationStrategy<V>,
  ): Unit {
    catch({
      admin.createTopic(topic.toNewTopic())
    }) { _: TopicExistsException ->
      logger.info { "Topic ${topic.name} already exists" }
    }
    registerSchema(topic.name, keyS, valueS)
  }

  private fun <K, V> registerSchema(
    topic: String,
    keyS: SerializationStrategy<K>,
    valueS: SerializationStrategy<V>,
  ): Unit {
    val keySchema = Avro.default.schema(keyS)
    val valueSchema = Avro.default.schema(valueS)
    registryClient.register("$topic-key", AvroSchema(keySchema))
    registryClient.register("$topic-value", AvroSchema(valueSchema))
  }
}
