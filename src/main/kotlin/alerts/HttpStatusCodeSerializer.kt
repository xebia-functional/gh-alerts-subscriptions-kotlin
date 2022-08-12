package alerts

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

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
