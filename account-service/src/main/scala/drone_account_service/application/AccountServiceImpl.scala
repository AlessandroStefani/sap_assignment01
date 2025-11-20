package drone_account_service.application

import cats.effect.{Deferred, IO}
import cats.effect.std.Queue
import drone_account_service.application.AccountService
import drone_account_service.domain.Account
import drone_account_service.infrastructure.{AccountCommand, RegisterCommand, LoginCommand}

class AccountServiceImpl(queue: Queue[IO, AccountCommand]) extends AccountService {

  override def registerUser(username: String, password: String): IO[Account] =
    for {
      // 1. Creiamo la "promessa" di una risposta futura
      deferred <- Deferred[IO, Either[Throwable, Account]]

      // 2. Inviamo il comando alla coda dell'Actor
      _ <- queue.offer(RegisterCommand(username, password, deferred))

      // 3. Ci mettiamo in attesa (senza bloccare il thread) che l'Actor completi la Deferred
      result <- deferred.get

      // 4. Se dentro c'è un errore (es. utente esiste), lo lanciamo, altrimenti ritorniamo l'account
      account <- IO.fromEither(result)
    } yield account

  override def loginUser(username: String, password: String): IO[Boolean] =
    for {
      deferred <- Deferred[IO, Boolean]
      _ <- queue.offer(LoginCommand(username, password, deferred))
      result <- deferred.get
    } yield result
}
