package alerts

import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.containers.wait.strategy.WaitStrategy
import java.io.File

sealed class Service {
    abstract val name: String
    abstract val port: Int
    abstract val waitStrategy: WaitStrategy

    companion object {
        val defaultWaitStrategy: WaitAllStrategy = WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY)
            .apply { withStrategy(Wait.forListeningPort()) }
    }

    class Postgres : Service() {
        override val name: String = "postgres"
        override val port: Int = 5432
        override val waitStrategy: WaitStrategy = defaultWaitStrategy
    }

    class Zookeeper : Service() {
        override val name: String = "zookeeper"
        override val port: Int = 2181
        override val waitStrategy: WaitStrategy = defaultWaitStrategy
    }

    class Broker : Service() {
        override val name: String = "broker"
        override val port: Int = 9092
        override val waitStrategy: WaitStrategy = defaultWaitStrategy
    }

    class SchemaRegistry : Service() {
        override val name: String = "schema-registry"
        override val port: Int = 8081
        override val waitStrategy: WaitStrategy = defaultWaitStrategy
    }
}

class ComposeContainer(
    composeFiles: List<File>
) : DockerComposeContainer<ComposeContainer>(composeFiles) {
    companion object {
        fun container(
            composeFiles: List<File>, services: List<Service>
        ): ComposeContainer =
            ComposeContainer(composeFiles)
                .withLocalCompose(false)
                .apply { services.forEach { withExposedService(it.name, it.port, it.waitStrategy) } }
    }
}
