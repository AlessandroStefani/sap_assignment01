package drone_api_gateway.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import drone_api_gateway.domain.Account

@OutBoundPort
trait AccountService:
  
  def registerUser(userName: String, password: String): IO[Account]

  def loginUser(userName: String, password: String): IO[Boolean]
  
