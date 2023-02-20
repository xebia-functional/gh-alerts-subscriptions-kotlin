import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

@Suppress("UnstableApiUsage")
val Provider<PluginDependency>.pluginId: String
    get() = get().pluginId

@Suppress("UnstableApiUsage")
val Provider<MinimalExternalModuleDependency>.asString: String
    get() = get().run {
        "${module.group}:${module.name}:${versionConstraint}"
    }

infix fun <T> Property<T>.by(value: T) {
    set(value)
}
