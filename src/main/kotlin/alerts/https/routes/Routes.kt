package alerts.https.routes

import alerts.persistence.SlackUserId
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

object Routes {
  @Resource("/ping")
  class Health
  
  @Resource("/metrics")
  class Metrics
  
  @Resource("/openapi")
  class OpenApi
  
  @Resource("/swagger")
  class Swagger
  
  @Resource("/slack/command")
  class Slack
  
  @Serializable
  @Resource("/subscription")
  class Subscription(val slackUserId: SlackUserId)
}
