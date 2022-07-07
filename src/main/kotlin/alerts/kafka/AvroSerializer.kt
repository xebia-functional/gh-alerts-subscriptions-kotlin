package alerts.kafka

import com.github.avrokotlin.avro4k.Avro
import kotlinx.serialization.KSerializer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

class AvroSerializer<A>(
  private val serializer: KSerializer<A>,
  private val avro: Avro = Avro.default,
) : Serializer<A>, Deserializer<A> {
  
  override fun deserialize(topic: String?, data: ByteArray): A =
    avro.decodeFromByteArray(serializer, data)
  
  override fun serialize(topic: String?, data: A): ByteArray =
    avro.encodeToByteArray(serializer, data)
  
  @Suppress("EmptyFunctionBlock")
  override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
  }
  
  @Suppress("EmptyFunctionBlock")
  override fun close() {
  }
}
