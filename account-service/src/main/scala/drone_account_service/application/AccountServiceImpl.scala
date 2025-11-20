package drone_account_service.application

import cats.effect.{Deferred, IO}
import cats.effect.std.Queue
import drone_account_service.application.AccountService
import drone_account_service.domain.Account
import drone_account_service.infrastructure.{AccountCommand, RegisterCommand, LoginCommand}

class AccountServiceImpl(queue: Queue[IO, AccountCommand]) extends AccountService {

  override def registerUser(username: String, password: String): IO[Account] =
    for {
      deferred <- Deferred[IO, Either[Throwable, Account]]
      _ <- queue.offer(RegisterCommand(username, password, deferred))
      result <- deferred.get
      account <- IO.fromEither(result)
    } yield account

  override def loginUser(username: String, password: String): IO[Boolean] =
    for {
      deferred <- Deferred[IO, Boolean]
      _ <- queue.offer(LoginCommand(username, password, deferred))
      result <- deferred.get
    } yield result
}
