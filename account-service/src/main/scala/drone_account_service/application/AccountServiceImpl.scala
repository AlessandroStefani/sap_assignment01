package drone_account_service.application

import cats.effect.IO
import drone_account_service.domain.Account

class AccountServiceImpl(repo: AccountRepository) extends AccountService:

  private var loggedUsers: List[String] = List.empty

  override def registerUser(username: String, password: String): IO[Account] =
    repo.register(username, password)

  override def loginUser(username: String, password: String): IO[Boolean] =
    repo.login(username, password).flatTap: isValid =>
      IO { if isValid && !loggedUsers.contains(username) then loggedUsers = username :: loggedUsers }

  override def logoutUser(username: String): IO[Boolean] = IO {
    if loggedUsers.contains(username) then
      loggedUsers = loggedUsers.filterNot(_ == username)
      true
    else false
  }