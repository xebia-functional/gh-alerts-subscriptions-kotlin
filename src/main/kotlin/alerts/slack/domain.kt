package alerts.slack

import alerts.user.SlackUserId
import alerts.subscription.Repository
import kotlinx.serialization.Serializable

@Serializable
enum class Command { Subscribe }

@Serializable
data class SlashCommand(
  val userId: SlackUserId,
  val command: Command,
  val repo: Repository,
)
