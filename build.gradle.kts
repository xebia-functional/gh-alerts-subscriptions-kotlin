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
    dialect(libs.sqldelight.postgresql.asString)
  }
}

allprojects {
  setupDetekt()
}

repositories {
  mavenCentral()
  maven(url = "https://packages.confluent.io/maven/")
  // For Kotest Extensions Arrow Fx, remove if 1.1.3 is released
  maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
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

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "${JavaVersion.VERSION_11}"
      freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers" + "-opt-in=kotlin.RequiresOptIn"
    }
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
  implementation("io.arrow-kt:suspendapp:0.1.1-alpha.5")
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.ktor.client)
  implementation(libs.logback.classic)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.hikari)
  implementation(libs.postgresql)
  implementation(libs.flyway)
  implementation(libs.klogging)
  implementation(libs.avro4k)
  implementation(libs.kotlin.kafka)
  implementation(libs.kafka.schema.registry)
  implementation(libs.kafka.avro.serializer)
  implementation(libs.avro)
  implementation(libs.kotlinx.serialization.jsonpath)
  
  testImplementation(libs.bundles.ktor.client)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(libs.ktor.server.tests)
  testImplementation(libs.bundles.kotest)
}
