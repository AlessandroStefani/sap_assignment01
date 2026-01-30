package drone_account_service.application

import cats.effect.IO
import drone_account_service.domain.Account

class AccountServiceImpl(repo: AccountRepository) extends AccountService:
  
  override def registerUser(username: String, password: String): IO[Account] =
    repo.register(username, password)

  override def loginUser(username: String, password: String): IO[Boolean] =
    repo.login(username, password)

  override def logoutUser(username: String): IO[Boolean] =
    IO.pure(true)