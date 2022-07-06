package alerts.env

import java.lang.System.getenv

private const val PORT: Int = 8080
private const val JDBC_URL: String = "jdbc:postgresql://localhost:5432/alerts"
private const val JDBC_USER: String = "test"
private const val JDBC_PW: String = "test"

data class Env(
  val http: Http = Http(),
  val postgres: Postgres = Postgres(),
  val github: Github = Github(),
  val kafka: Kafka = Kafka()
) {

  data class Http(
    val host: String = getenv("host") ?: "0.0.0.0",
    val port: Int = getenv("PORT")?.toIntOrNull() ?: PORT,
  )

  data class Postgres(
    val url: String = getenv("POSTGRES_URL") ?: JDBC_URL,
    val username: String = getenv("POSTGRES_USER") ?: JDBC_USER,
    val password: String = getenv("POSTGRES_PASSWORD") ?: JDBC_PW,
  ) {
    val driver: String = "org.postgresql.Driver"
  }

  data class Github(
    val uri: String = "https://api.github.com",
    // TODO what do we do here if empty? Crash?
    val token: String? = getenv("GITHUB_TOKEN")
  )

  data class Kafka(
    val bootstrapServers: String = getenv("BOOTSTRAP_SERVERS") ?: "localhost:9092",
    val schemaRegistryUrl: String = getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:8081",
    val subscriptionTopic: Topic = Topic(getenv("SUBSCRIPTION_TOPIC") ?: "subscriptions", 1, 1),
    val eventTopic: Topic = Topic(getenv("EVENT_TOPIC") ?: "events", 1, 1),
    val notificationTopic: Topic = Topic(getenv("NOTIFICATION_TOPIC") ?: "notifications", 1, 1)
  ) {
    data class Topic(val name: String, val numPartitions: Int, val replicationFactor: Short)

    val eventConsumerGroupId = "github-event-consumer"
  }
}
