package drone_account_service.application

import cats.effect.IO
import drone_account_service.domain.Account

trait AccountService:

  def registerUser(username: String, password: String): IO[Account]

  def loginUser(username: String, password: String): IO[Boolean]

  def logoutUser(username: String): IO[Boolean]
