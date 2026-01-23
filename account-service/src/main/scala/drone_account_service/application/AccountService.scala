package drone_account_service.application

import cats.effect.IO
import common.exagonal.InBoundPort
import drone_account_service.domain.Account

@InBoundPort
trait AccountService:

  def registerUser(username: String, password: String): IO[Account]

  def loginUser(username: String, password: String): IO[Boolean]

  def logoutUser(username: String): IO[Boolean]
