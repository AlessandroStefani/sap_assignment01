package drone_account_service.infrastructure

import cats.effect.{Deferred, IO}
import drone_account_service.domain.Account

sealed trait AccountCommand

case class RegisterCommand(
                            username: String,
                            password: String,
                            replyTo: Deferred[IO, Either[Throwable, Account]]
                          ) extends AccountCommand
