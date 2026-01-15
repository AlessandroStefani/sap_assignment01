package drone_account_service.application

import cats.effect.{Deferred, IO}
import cats.effect.std.Queue
import drone_account_service.domain.Account
import drone_account_service.infrastructure.{AccountCommand, RegisterCommand}

class AccountServiceImpl(
                          commandQueue: Queue[IO, AccountCommand],
                          accountReader: IO[List[Account]]
                        ) extends AccountService:

  private var loggedUsers: List[String] = List.empty

  override def registerUser(username: String, password: String): IO[Account] =
    for
      deferred <- Deferred[IO, Either[Throwable, Account]]
      // WRITE SIDE: Send to Queue
      _ <- commandQueue.offer(RegisterCommand(username, password, deferred))
      result <- deferred.get
      account <- IO.fromEither(result)
    yield account

  override def loginUser(username: String, password: String): IO[Boolean] =
    // READ SIDE: Read directly from memory (via FileDatabase Ref)
    accountReader.flatMap { accounts =>
      val isValid = accounts.exists(a => a.username == username && a.password == password)
      IO {
        if isValid && !loggedUsers.contains(username) then
          loggedUsers = username :: loggedUsers
        isValid
      }
    }

  override def logoutUser(username: String): IO[Boolean] = IO {
    if loggedUsers.contains(username) then
      loggedUsers = loggedUsers.filterNot(_ == username)
      true
    else
      false
  }