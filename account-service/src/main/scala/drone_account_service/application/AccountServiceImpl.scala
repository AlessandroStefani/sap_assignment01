package drone_account_service.application

import cats.effect.IO
import drone_account_service.domain.Account

class AccountServiceImpl(repo: drone_account_service.application.AccountRepository) extends AccountService:

  private var loggedUsers: List[String] = List.empty

  override def registerUser(username: String, password: String): IO[Account] =
    repo.register(username, password)

  override def loginUser(username: String, password: String): IO[Boolean] =
    for
      isValid <- repo.login(username, password)
      _ <- IO {
        if isValid && !loggedUsers.contains(username) then
          loggedUsers = username :: loggedUsers
      }
    yield isValid

  override def logoutUser(username: String): IO[Boolean] = IO {
    if loggedUsers.contains(username) then
      loggedUsers = loggedUsers.filterNot(_ == username)
      true
    else
      false
  }