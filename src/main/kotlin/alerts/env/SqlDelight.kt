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
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway

suspend fun ResourceScope.SqlDelight(dataSource: DataSource): SqlDelight {
  val driver = closeable { dataSource.asJdbcDriver() }
  Flyway.configure().dataSource(dataSource).load().migrate()
  return SqlDelight(
    driver,
    Repositories.Adapter(repositoryIdAdapter),
    Subscriptions.Adapter(userIdAdapter, repositoryIdAdapter),
    Users.Adapter(userIdAdapter, slackUserIdAdapter)
  )
}

suspend fun ResourceScope.hikari(env: Env.Postgres): HikariDataSource = autoCloseable {
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
  crossinline encode: (value: A) -> B,
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)
    override fun encode(value: A): B = encode(value)
  }
