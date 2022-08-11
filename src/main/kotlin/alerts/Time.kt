package alerts

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface Time {
  suspend fun now(): LocalDateTime
  
  object SystemUTC : Time {
    override suspend fun now(): LocalDateTime =
      kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.UTC)
  }
}
