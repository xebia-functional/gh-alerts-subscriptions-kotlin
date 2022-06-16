import kotlinx.kover.api.KoverTaskExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.incremental.updateIncrementalCache

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  id(libs.plugins.kotlin.jvm.pluginId)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.jib)
  alias(libs.plugins.kotest.multiplatform)
  alias(libs.plugins.kover)
  id(libs.plugins.detekt.pluginId)
}

val main by extra("alerts.MainKt")

application {
  mainClass by main
}

sqldelight {
  database("SqlDelight") {
    packageName = "alerts.sqldelight"
    dialect = libs.sqldelight.postgresql.asString
  }
}

allprojects {
  setupDetekt()
}

repositories {
  mavenCentral()
}

jib {
  from {
    image = "openjdk:11-jre-slim-buster"
  }
  container {
    ports = listOf("8080")
    mainClass = main
  }
  to {
    image = "ghcr.io/47deg/github-alerts-subscriptions-kotlin"
    tags = setOf("latest")
  }
}

java.sourceCompatibility = JavaVersion.VERSION_11

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "${JavaVersion.VERSION_11}"
    }
    sourceCompatibility = "${JavaVersion.VERSION_11}"
    targetCompatibility = "${JavaVersion.VERSION_11}"
  }

  test {
    useJUnitPlatform()
    extensions.configure(KoverTaskExtension::class) {
      includes = listOf("alerts.*")
      excludes = listOf("alerts.sqldelight")
    }
  }
}

dependencies {
  implementation(libs.bundles.arrow)
  implementation(libs.bundles.ktor.server)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)

  testImplementation(libs.bundles.ktor.client)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.bundles.kotest)
}
