package alerts.env

import alerts.subscription.RepositoryId
import alerts.user.SlackUserId
import alerts.user.UserId
import alerts.sqldelight.Repositories
import alerts.sqldelight.SqlDelight
import alerts.sqldelight.Subscriptions
import alerts.sqldelight.Users
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromCloseable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

fun SqlDelight(env: Env.Postgres): Resource<SqlDelight> =
  resource {
    val dataSource = hikari(env).bind()
    val driver = Resource.fromCloseable(dataSource::asJdbcDriver).bind()
    Flyway.configure().dataSource(dataSource).load().migrate()
    SqlDelight(
      driver,
      Repositories.Adapter(repositoryIdAdapter),
      Subscriptions.Adapter(userIdAdapter, repositoryIdAdapter),
      Users.Adapter(userIdAdapter, slackUserIdAdapter)
    )
  }

private fun hikari(env: Env.Postgres): Resource<HikariDataSource> =
  Resource.fromCloseable {
    HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = env.url
        username = env.username
        password = env.password
        driverClassName = env.driver
      }
    )
  }

private val repositoryIdAdapter = columnAdapter(::RepositoryId, RepositoryId::serial)
private val userIdAdapter = columnAdapter(::UserId, UserId::serial)
private val slackUserIdAdapter = columnAdapter(::SlackUserId, SlackUserId::slackUserId)

private inline fun <A : Any, B> columnAdapter(
  crossinline decode: (databaseValue: B) -> A,
  crossinline encode: (value: A) -> B
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)
    override fun encode(value: A): B = encode(value)
  }
