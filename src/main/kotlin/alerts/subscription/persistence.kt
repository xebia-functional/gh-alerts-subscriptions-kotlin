package alerts.subscription

import alerts.user.UserId
import arrow.core.Either
import arrow.core.traverse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component

@Table(value = "subscriptions")
data class SubscriptionDto(
  @Id val id: Long,
  val userId: UserId,
  val repository: Repository,
  val createdAt: LocalDateTime
)

interface SubscriptionRepo : CoroutineCrudRepository<SubscriptionDto, Long> {
  fun findAllByRepository(repository: Repository): Flow<SubscriptionDto>
}

interface SubscriptionsPersistence {
  suspend fun findAll(user: UserId): List<Subscription>
  suspend fun findSubscribers(repository: Repository): List<UserId>
  suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit>
  suspend fun unsubscribe(user: UserId, repositories: List<Repository>): Unit

  suspend fun subscribe(user: UserId, subscription: Subscription): Either<UserNotFound, Unit> =
    subscribe(user, listOf(subscription))

  suspend fun unsubscribe(user: UserId, repositories: Repository): Unit =
    unsubscribe(user, listOf(repositories))
}

@Component
class DefaultSubscriptionsPersistence(
  private val repo: SubscriptionRepo
): SubscriptionsPersistence {
  override suspend fun findAll(user: UserId): List<Subscription> =
    repo.findAllById(listOf(user.serial)).map { subscription ->
      Subscription(subscription.repository, subscription.createdAt)
    }.toList()

  override suspend fun findSubscribers(repository: Repository): List<UserId> =
    repo.findAllByRepository(repository).map { subscription ->
      UserId(subscription.id)
    }.toList()

  override suspend fun subscribe(user: UserId, subscription: List<Subscription>): Either<UserNotFound, Unit> {
    TODO("Not yet implemented")
//    subscription.traverse { (repository, subscribedAt) ->
//      val repoId = repo.save(Repository(repository.owner, repository.name))
//    }
  }

  override suspend fun unsubscribe(user: UserId, repositories: List<Repository>) {
    TODO("Not yet implemented")
  }

}
