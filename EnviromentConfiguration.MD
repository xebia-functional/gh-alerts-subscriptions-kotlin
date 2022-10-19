# Environment / Configuration

When setting up a server, we often want to provide specific configurations depending on the environment we're running it in. 
For example, the IP address and port on which the server will be available.

```text
DEV: 127.0.0.1:8080
ACC: 192.168.0.105:8085
PROD: 12.14.16.18:89127
```

Additionally, we might need to integrate our server with many other services 
like databases, Kafka, Redis, or some other microservices.

These external resources are also running at different network coordinates 
and credentials based on the environment the server is running in.

For example, `TestContainers` vs `docker-compose` vs `Google Cloud Platform K8S`.
Which we could call `TEST`, `DEV` and `ACC`/`PROD` environment respectively.

Most commonly used techniques for configuring the application is either use `data class` to model the _Enviroment Configuration_ in a typed way and manually configuring frameworks, or letting frameworks automatically read configuration.

When writing your own domain model you can either load the config programatically, or using formatted files with a library to convert the file into your typed model.

Below we explore both options.

## Plain Kotlin

As a first solution, and also used inside this project we're going to use plain Kotlin.
We prefer using the simplest solution possible, and with plain Kotlin we can get pretty far.

In this project we rely on `Postgres`,
so let's see how we can model our _own http configuration_, and the _database configuration_.

```kotlin
import java.lang.System.getenv

data class Env(val http: Http = Http(), val postgres: Postgres = Postgres())

data class Http(
  val host: String = getenv("host") ?: "0.0.0.0",
  val port: Int = getenv("PORT")?.toIntOrNull() ?: 8080,
)

data class Postgres(
  val url: String = getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/alerts",
  val username: String = getenv("POSTGRES_USER") ?: "test",
  val password: String = getenv("POSTGRES_PASSWORD") ?: "test",
)
```

As you can see above, we have a nicely typed `Env` type. There are a couple of trade-offs, or things going on here:

1. We're directly initialising the values in the constructor. We ignore the fact that `System.getenv` is side-effecting.
   This is non-problematic since we want to fail-fast when the configuration is not available.
   Below we discuss on how to evolve this pattern further.

2. This method relies on `System.getenv` or defaults to a single value.
   So we only encode two different flavors, in testing we have to rely on `(TestContainer) -> Postgres` to build our `Env`.

### Evolve plain Kotlin

There are two things we've ignored in the previous section which is _side effects_, and error tracking.
`System.getenv` is already a _side effect_, but wrapping it inside `suspend` doesn't offer us a lot of benefit.

If you require accessing a remote config, feature flags, or reading some configuration from disk, then `suspend` might offer more benefits.
For example, you could read remote configs in parallel.

```kotlin
suspend fun env(): Env =
  parZip({ remoteHttp() }, { remotePostgres() }) { http, postgres -> Env(http, postgres) }
```

Additionally, when building configurations you might want to know which properties were missing before crashing the server.
This can be really useful when debugging, so that you get a list of all missing properties with a clear message.

```kotlin
import java.lang.System.getenv

fun env(name: String) : ValidatedNel<String, String> =
   getenv(name)?.valid() ?: "\"$name\" configuration missing".invalidNel()

fun <A : Any> env(name: String, transform: (String) -> A?) : ValidatedNel<String, A> =
   env(name).andThen { transform(it)?.valid() ?: "\"$name\" configuration found with $it".invalidNel() }

fun http(): ValidatedNel<String, Http> = 
  env("host").zip(env("PORT") { it.toIntOrNull() }, ::Http)

fun postgres(): ValidatedNel<String, Postgres> =
  env("POSTGRES_URL").zip(env("POSTGRES_USER"), env("POSTGRES_PASSWORD"), ::Postgres)

fun env(): ValidatedNel<String, Env> = http().zip(postgres(), ::Env)

fun ValidatedNel<String, Env>.getOrThrow(): Env =
   fold({
      val message = it.joinToString(prefix = "Environment failed to load:\n", separator = "\n")
      throw RuntimeException(message)
   }) { it }

fun main(): Unit {
   env().getOrThrow()
}
```

When we run the above example we will see following log in the console:

```text
Exception in thread "main" java.lang.RuntimeException: Environment failed to load:
"host" configuration missing
"PORT" configuration missing
"POSTGRES_URL" configuration missing
"POSTGRES_USER" configuration missing
"POSTGRES_PASSWORD" configuration missing
	at alerts.MainKt.getOrThrow(main.kt:55)
	at alerts.MainKt$main$1.invokeSuspend(main.kt:59)
	at alerts.MainKt$main$1.invoke(main.kt)
	at alerts.MainKt$main$1.invoke(main.kt)
	at arrow.continuations.SuspendAppKt$SuspendApp$1$job$1.invokeSuspend(SuspendApp.kt:37)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:106)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:570)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:750)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:677)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:664)
```

This clearly shows us what is going wrong with the environment, and which configurations we are missing.  
This pattern can easily be combined with `suspend`,
and thus gives us all powers we typically look for when building typed configuration libraries.

## File based configuration (hocon, yml, ...)

Perhaps the most common approach is to use configuration files inside the `resource` folder, and letting the framework read it, or use a library specifically for decoding configuration files.

In the example below we're going to use the [Hocon format by Lightbend](https://github.com/lightbend/config/blob/main/HOCON.md), a popular format for configuring servers.
In combination with [Hoplite](https://github.com/sksamuel/hoplite) to automatically _decode_ the hocon file into our own _data class_ domain.

Let's take the same example from before, but adjust it to use Hocon.

```kotlin
data class Env(val http: Http, val postgres: Postgres)
data class Http(val host: String, val port: Int)
data class Postgres(val url: String, val username: String, val password: String)

fun main() {
   val env = ConfigLoader().loadConfigOrThrow<Env>("/application.conf")
   println(env)
}
```

When using HOCON we now need to split our code over 2 files.
The first one defining our data classes in our `main` code, this is the snippet above.
The second one defining our actual configuration in our `main/resources/application.conf` directory, in the snippet below. 


```text
http {
  host = "127.0.0.1"
  host = ${?host}
  port = 8080
  port = ${?PORT}
}

postgres {
  url = "jdbc:postgresql://localhost:5432/alerts"
  url = ${?POSTGRES_URL}
  username = "test"
  username = ${?POSTGRES_USER}
  password = "test"
  password = ${?POSTGRES_PASSWORD}
}
```

In the `application.conf` often the same approach is used as above,
where we define values through _optional environment variables_ while providing default values.

Here we have to read it the other way around though,
before we were using the elvis operator `?:` to say `optional env variable ?: default value`.

Hocon reads as follows, we define an `url = default-value` and we attempt to override with an optional `?` value.

```text
url = "jdbc:postgresql://localhost:5432/alerts"
url = ${?POSTGRES_URL}
```

Then we ask a library, or the framework to read in a specific configuration,
we could've also used `application-prod.conf`, `application-dev.conf`, or `application-acc.conf`, ... 

```kotlin
val env = ConfigLoader().loadConfigOrThrow<Env>("/application.conf")
println(env)
```

In the case below, none of the _optional environment variables_ were present.

```text
Env(http=Http(host=127.0.0.1, port=8080), postgres=Postgres(url=jdbc:postgresql://localhost:5432/alerts, username=test, password=test))
```

In this example we used `"com.sksamuel.hoplite:hoplite-hocon:2.5.2"`, but `hoplite` also has support for `yml`, `json`, `toml`, and `Java Properties`.

Alternatively, you can have Ktor or Spring read in the application configuration, and they offer some utilities to access non-framework configuration values.
See [the original Kotlin Server Side Spring project](https://github.com/47deg/kotlin-server-side/blob/master/application/src/main/resources/application.conf) as another example. 
