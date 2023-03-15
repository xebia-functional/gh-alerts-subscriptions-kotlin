import kotlinx.kover.api.KoverTaskExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") plugins {
  application
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.kotest.multiplatform)
  alias(libs.plugins.kover)
  id(libs.plugins.detekt.pluginId)
  id("org.springframework.boot") version "3.0.4"
  id("io.spring.dependency-management") version "1.1.0"
  kotlin("jvm")
  kotlin("plugin.spring") version "1.7.22"
}

buildscript {
  dependencies {
    classpath("org.apache.commons:commons-compress:1.22")
  }
}

val main by extra("alerts.MainKt")

application {
  mainClass by main
}


allprojects {
  setupDetekt()
}

repositories {
  mavenCentral()
  maven(url = "https://packages.confluent.io/maven/")
  // For Kotest Extensions Arrow Fx, remove if 1.1.3 is released
  maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
  maven(url = "https://jitpack.io")
}


java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions.freeCompilerArgs += listOf(
    "-Xopt-in=kotlin.RequiresOptIn",
    "-Xopt-in=kotlin.OptIn",
    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
    "-Xopt-in=kotlinx.coroutines.FlowPreview"
  )
}

tasks {
  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "${JavaVersion.VERSION_17}"
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xcontext-receivers",
        "-opt-in=kotlinx.coroutines.FlowPreview"
      )
    }
  }
  
  test {
    useJUnitPlatform()
    extensions.configure(KoverTaskExtension::class) {
      includes.add("alerts.*")
    }
  }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.postgresql:r2dbc-postgresql:1.0.1.RELEASE")
  implementation("org.jetbrains.kotlin:kotlin-stdlib")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("io.projectreactor.kafka:reactor-kafka:1.3.15")

  implementation(libs.bundles.arrow)
  implementation(libs.logback.classic)
  implementation(libs.postgresql)
  implementation(libs.flyway)
  implementation(libs.klogging)
  implementation(libs.avro4k)

  implementation(libs.kafka.schema.registry)
  implementation(libs.kafka.avro.serializer)
  implementation(libs.avro)

  implementation(libs.kotlinx.serialization.jsonpath)

  implementation(libs.micrometer.prometheus)

  implementation(libs.kotlinx.datetime)

  runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.89.Final:osx-aarch_64")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  testImplementation(libs.bundles.kotest)
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
