package drone_account_service.infrastructure

import cats.effect.*
import cats.effect.std.Queue
import common.exagonal.Adapter
import drone_account_service.application.AccountRepository
import drone_account_service.domain.Account
import fs2.io.file.{Files, Path}
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*

@Adapter
private class FileDatabase(private val queue: Queue[IO, AccountCommand]) extends AccountRepository:

  override def register(username: String, password: String): IO[Account] =
    for
      deferred <- Deferred[IO, Either[Throwable, Account]]
      _ <- queue.offer(RegisterCommand(username, password, deferred))
      result <- deferred.get
      account <- IO.fromEither(result)
    yield account

  def login(username: String, password: String): IO[Boolean] =
    for
      deferred <- Deferred[IO, Boolean]
      _ <- queue.offer(LoginCommand(username, password, deferred))
      isValid <- deferred.get
    yield isValid

object FileDatabase:
  def make(filePath: String): Resource[IO, AccountRepository] =
    val path = Path(filePath)

    Resource.eval(for
      _ <- initializeDb(path)
      initialState <- loadFromFile(path)
      stateRef <- Ref.of[IO, List[Account]](initialState)
      queue <- Queue.bounded[IO, AccountCommand](100)
      _ <- processQueueLoop(queue, stateRef, path).start
      _ <- IO.println(s"DB Persistence started on file: $filePath")
    yield new FileDatabase(queue))

  private def initializeDb(path: Path): IO[Unit] =
    Files[IO].exists(path).flatMap: exists =>
      if !exists then
        IO.println(s"File DB non trovato. Creazione di un nuovo file vuoto: $path") *>
          fs2.Stream("[]")
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(path))
            .compile.drain
      else
        IO.println(s"File DB trovato: $path")

  private def processQueueLoop(
                                queue: Queue[IO, AccountCommand],
                                stateRef: Ref[IO, List[Account]],
                                path: Path
                              ): IO[Unit] =
    queue.take.flatMap {
        case RegisterCommand(user, pass, replyTo) =>
          stateRef.get.flatMap { currentAccounts =>
            if currentAccounts.exists(_.username == user) then
              replyTo.complete(Left(new RuntimeException(s"User $user already exists")))
            else
              val newId = currentAccounts.map(_.id).maxOption.getOrElse(0) + 1
              val newAccount = Account(newId, user, pass)
              val updatedList = currentAccounts :+ newAccount

              for
                _ <- stateRef.set(updatedList)
                _ <- saveToFile(path, updatedList)
                _ <- replyTo.complete(Right(newAccount))
              yield ()
          }

        case LoginCommand(user, pass, replyTo) =>
          stateRef.get.flatMap { accounts =>
            val isValid = accounts.exists(a => a.username == user && a.password == pass)
            replyTo.complete(isValid)
          }
      }
      .handleErrorWith(e => IO.println(s"Persistence Error: $e"))
      .flatMap(_ => processQueueLoop(queue, stateRef, path))

  private def loadFromFile(path: Path): IO[List[Account]] =
    Files[IO].readAll(path)
      .through(fs2.text.utf8.decode)
      .compile.string
      .map(decode[List[Account]](_).getOrElse(List.empty))

  private def saveToFile(path: Path, accounts: List[Account]): IO[Unit] =
    val json = accounts.asJson.spaces2
    fs2.Stream(json)
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(path))
      .compile.drain