package alerts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.springframework.http.HttpStatusCode

object HttpStatusCodeSerializer : KSerializer<HttpStatusCode> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HttpStatusCode") {
    element<Int>("value")
  }

  override fun deserialize(decoder: Decoder): HttpStatusCode =
    decoder.decodeStructure(descriptor) {
      var value: Int? = null
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> value = decodeIntElement(descriptor, 0)
          CompositeDecoder.DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      HttpStatusCode.valueOf(requireNotNull(value) { "Value property missing HttpStatusCode" })
    }

  override fun serialize(encoder: Encoder, value: HttpStatusCode) =
    encoder.encodeStructure(descriptor) {
      encodeIntElement(descriptor, 0, value.value())
    }
}
