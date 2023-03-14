package alerts

import io.kotest.core.extensions.Extension
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class IntegrationTestBase(body: StringSpec.() -> Unit = {}) : StringSpec(body) {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
        super.afterSpec(spec)
    }

    companion object {
        internal val container =
            ComposeContainer.container(
                listOf(
                    File("docker-compose.yml"),
                    File("docker-compose.local.yml")
                ),
                listOf(
                    Service.Postgres(),
                    Service.Zookeeper(),
                    Service.Kafka(),
                    Service.SchemaRegistry()
                )
            ).also { it.start() }
    }
}