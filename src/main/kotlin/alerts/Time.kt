package alerts

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun interface Time {
  fun now(): LocalDateTime
  
  object UTC : Time by TimeZonedTime(TimeZone.UTC)
  
  companion object {
    fun currentSystemDefault(): Time =
      TimeZonedTime(TimeZone.currentSystemDefault())
  }
}

private class TimeZonedTime(private val timeZone: TimeZone) : Time {
  override fun now(): LocalDateTime =
    kotlinx.datetime.Clock.System.now().toLocalDateTime(timeZone)
}
