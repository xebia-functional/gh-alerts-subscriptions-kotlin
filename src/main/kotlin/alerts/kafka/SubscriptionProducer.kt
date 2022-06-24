package alerts.kafka

import alerts.env.Env
import alerts.persistence.Repository
import arrow.fx.coroutines.Resource

interface SubscriptionProduce {
  suspend fun publish(repo: Repository): Unit
  suspend fun delete(repo: Repository): Unit
}

fun subscriptionProducer(
  config: Env.Kafka,
): Resource<SubscriptionProduce> {
  TODO()
}
