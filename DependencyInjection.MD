# Dependency Injection & Graceful Shutdown

When building any application, there are a number of dependencies that need to be injected.
Typically, many of these dependencies have some sort of "lifecycle" that needs to be carefully controlled.

For example, when we require communicating to a `Database` we need to set up a `DataSource`.
This `DataSource` creates a pool of `Connection`s that is used to communicate with the database,
and it needs to be shut down when the application is shut down.
Otherwise, the `DataSource` will remain open and the database will keep the connections open for us until they time out.

This is a problem when we're running the application in a distributed environment, because pods can be restarted or scaled up or down.
If we have _many_ pods that keep their connections open, even after shutting down the application,
then we'll get a lot of connections open and the database will be overwhelmed.

For this reason we construct our _dependency graph_ using Arrow's [`Resource`](https://arrow-kt.io/docs/apidocs/arrow-fx-coroutines/arrow.fx.coroutines/-resource/),
and we run our application using [`SuspendApp`](https://arrow-kt.github.io/suspendapp/).

We can then safely rely on `Resource.use` to manage the lifecycle of the dependencies for us,
with `SuspendApp` giving us strong guarantees about gracefully shutting down the dependencies when the application is asked to shut down.

```kotlin
import arrow.continuations.SuspendApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun main() = SuspendApp {
  val config = TODO("load configuration")
  Resource({ DataSource(config) }) { ds, _: Exitcase ->
    ds.close()
  }.use { ds: DataSource ->
    TODO("use the DataSource")
  }
}
```

The following program guarantees us that when `SuspendApp` is requested to close,
by `SIGINT` or `SIGTERM` that it will _first close the DataSource before exiting the process_.

So now that we know how to use `Resource` and `SuspendApp`, we can implement our _entire_ dependency graph by composing `Resource`.
Below we see a small example, using a subset of what you can find in [`alerts.env.Dependencies.kt`](src/main/kotlin/alerts/env/Dependencies.kt).

```kotlin
class Dependencies(val persistence: UserPersistence, val metrics: PrometheusMeterRegistry)

fun SqlDelight(env: Env.Postgres): Resource<SqlDelight> = TODO()

fun Metrics(): Resource<PrometheusMeterRegistry> = 
  Resource({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _: ExitCase -> p.close() }

fun Dependencies(): Resource<Dependencies> = resource {
  val metrics = Metrics().bind()
  val slackUsersCounter: Counter =
    Counter
      .build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(appMicrometerRegistry.prometheusRegistry)
  
  val database = SqlDelight().bind()
  val persistence = userPersistence(sqlDelight.usersQueries, slackUsersCounter)
  
  Dependencies(persistence, metrics)
}
fun main() = SuspendApp {
  Dependencies().use {
    TODO("Use UserPersistence and PrometheusMeterRegistry")
  }
}
```

In the example above,
you can clearly see how you can easily compose very complex dependencies while their lifecycle is automatically taken care of.

We can improve this further by removing the need for calling _bind, which has been proposed for the Arrow 2.0 encoding of `Resource` as well.

The make the _capabilities_ of `Resource` available all we have to do is use `suspend` and have `ResourceScope` in our context.
Most conveniently this is currently done using extension functions,
but it could also be done by passing a parameter and using `with` or using `context receivers`.

```kotlin
suspend fun ResourceScope.SqlDelight(env: Env.Postgres): SqlDelight = TODO()

suspend fun ResourceScope.Metrics(): PrometheusMeterRegistry =
  install({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _: ExitCase -> p.close() }

suspend fun Dependencies(): Dependencies {
  val metrics = Metrics()
  val slackUsersCounter: Counter =
    Counter
      .build()
      .name("slack_users_counter")
      .help("Number of Slack users registered with our service")
      .register(appMicrometerRegistry.prometheusRegistry)
  
  val database = SqlDelight()
  val persistence = userPersistence(sqlDelight.usersQueries, slackUsersCounter)
  
  Dependencies(persistence, metrics)
}

fun main() = SuspendApp {
  resourceScope {
    val dependencies = Dependencies()
    TODO("Use UserPersistence and PrometheusMeterRegistry")
  }
}
```

In the example above you can see that the "clutter", boilerplate or "flatMap/bind dance" has disappeared, and regular imperative code remains.

- `Resource({ }, { }).bind()` has been replaced by `suspend fun install`, which _installs_ the `Resource` into the `Scope`.
- `Resource.use` is replaced by `resourceScope { }`
