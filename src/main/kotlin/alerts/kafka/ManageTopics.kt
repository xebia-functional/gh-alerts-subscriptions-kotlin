package alerts.kafka

import alerts.env.Env
import alerts.github.GithubEvent
import alerts.github.SlackNotification
import alerts.catch
import alerts.subscription.SubscriptionEvent
import alerts.subscription.SubscriptionKey
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
  registryClientCacheSize: Int = 100,
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
    createTopicsOrLog(config.subscriptionTopic, config.eventTopic, config.notificationTopic)
    registerSchema(config.subscriptionTopic, SubscriptionKey.serializer(), SubscriptionEvent.serializer())
    registerSchema(config.eventTopic, GithubEvent.serializer())
    registerSchema(config.notificationTopic, SlackNotification.serializer())
  }
  
  private fun Env.Kafka.Topic.toNewTopic(): NewTopic = NewTopic(name, numPartitions, replicationFactor)
  
  private suspend fun createTopicsOrLog(vararg topics: Env.Kafka.Topic): Unit =
    topics.forEach { topic ->
      catch({
        admin.createTopic(topic.toNewTopic())
      }) { _: TopicExistsException ->
        logger.info { "Topic ${topic.name} already exists" }
      }
    }
  
  private fun <V> registerSchema(
    topic: Env.Kafka.Topic,
    valueS: SerializationStrategy<V>,
  ) {
    val valueSchema = Avro.default.schema(valueS)
    registryClient.register("${topic.name}-value", AvroSchema(valueSchema))
  }
  
  private fun <K, V> registerSchema(
    topic: Env.Kafka.Topic,
    keyS: SerializationStrategy<K>,
    valueS: SerializationStrategy<V>,
  ) {
    val keySchema = Avro.default.schema(keyS)
    registryClient.register("${topic.name}-key", AvroSchema(keySchema))
    registerSchema(topic, valueS)
  }
}
