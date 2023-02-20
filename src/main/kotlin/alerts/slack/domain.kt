package alerts.slack

import alerts.subscription.Repository
import alerts.user.SlackUserId
import kotlinx.serialization.Serializable

@Serializable
enum class Command { Subscribe }

@Serializable
data class SlashCommand(
  val userId: SlackUserId,
  val command: Command,
  val repo: Repository,
)
