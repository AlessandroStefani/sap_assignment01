package drone_account_service.application

import cats.effect.IO
import drone_account_service.domain.Account
import common.exagonal.OutBoundPort

@OutBoundPort
trait AccountRepository:
  def register(username: String, password: String): IO[Account]
  def login(username: String, password: String): IO[Boolean]
