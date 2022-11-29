package alerts.env

import alerts.user.SlackUserId
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

object Routes {
  @Resource("/ping")
  class Health
  
  @Resource("/metrics")
  class Metrics

  @Resource("/swagger")
  class Swagger
  
  @Resource("/slack/command")
  class Slack
  
  @Serializable
  @Resource("/subscription")
  class Subscription(val slackUserId: SlackUserId)
}
