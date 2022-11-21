package alerts

import arrow.core.Either
import arrow.core.left
import arrow.core.nonFatalOrThrow
import arrow.core.right
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Catches a [Throwable], and allows mapping to [E].
 * In the case `null` is returned the original [Throwable] is rethrown,
 * otherwise `Either<E, A>` is returned.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <reified T : Throwable, E, A> catch(
  block: () -> A,
  transform: (T) -> E,
): Either<E, A> = try {
  block().right()
} catch (e: Throwable) {
  val nonFatal = e.nonFatalOrThrow()
  if (nonFatal is T) {
    transform(nonFatal).left()
  } else throw e
}

object HttpStatusCodeSerializer : KSerializer<HttpStatusCode> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HttpStatusCode") {
    element<Int>("value")
    element<String>("description")
  }
  
  override fun deserialize(decoder: Decoder): HttpStatusCode =
    decoder.decodeStructure(descriptor) {
      var value: Int? = null
      var description: String? = null
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> value = decodeIntElement(descriptor, 0)
          1 -> description = decodeStringElement(descriptor, 1)
          CompositeDecoder.DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      HttpStatusCode(
        requireNotNull(value) { "Value property missing HttpStatusCode" },
        requireNotNull(description) { "Description property missing HttpStatusCode" }
      )
    }
  
  override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: HttpStatusCode) =
    encoder.encodeStructure(descriptor) {
      encodeIntElement(descriptor, 0, value.value)
      encodeStringElement(descriptor, 1, value.description)
    }
}
