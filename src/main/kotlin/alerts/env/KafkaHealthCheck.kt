package alerts.env

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.kafka.KafkaClusterHealthCheck
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG

suspend fun ResourceScope.kafkaHealthCheck(env: Env.Kafka): HealthCheck =
  KafkaClusterHealthCheck(autoCloseable {
    AdminClient.create(mapOf(BOOTSTRAP_SERVERS_CONFIG to env.bootstrapServers))
  })
