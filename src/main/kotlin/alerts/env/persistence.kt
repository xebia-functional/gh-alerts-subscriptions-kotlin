package alerts.env

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun hikari(env: Env.Postgres): Resource<HikariDataSource> =
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
