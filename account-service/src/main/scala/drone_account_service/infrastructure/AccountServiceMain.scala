package drone_account_service.infrastructure

import cats.effect.*
import com.comcast.ip4s.*
import drone_account_service.application.AccountService
import drone_account_service.domain.{Account, AccountPost}

import scala.language.postfixOps
import drone_account_service.application.AccountServiceImpl
import drone_account_service.infrastructure.FileDatabase
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger

object AccountServiceMain extends IOApp:

  private var loggedIn: List[AccountPost] = List.empty

  private def accountRoutes(service: AccountService): HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "test" / "login" =>
      req.as[AccountPost].flatMap { input =>
        service.loginUser(input.name, input.password).flatMap { isValid =>
          if isValid then
            if loggedIn.contains(input) then Found("Already logged in")
            else
              loggedIn = input :: loggedIn
              Ok("Login successful")
          else Forbidden("Invalid credentials")
        }
      }

    case req @ POST -> Root / "test" / "register" =>
      req.as[AccountPost].flatMap { inputData =>
        service.registerUser(inputData.name, inputData.password).flatMap { newAccount =>
          Ok(newAccount)
        }.handleErrorWith {
          case e: RuntimeException if e.getMessage.contains("exists") =>
            Conflict(s"Errore: ${e.getMessage}")
          case e =>
            InternalServerError(s"Errore imprevisto: ${e.getMessage}")
        }
      }

    case _ => NotFound("Rotta non trovata")
  }

  def run(args: List[String]): IO[ExitCode] =
    FileDatabase.make("src/main/resources/accounts.json").use { commandQueue =>
      val accountService = new AccountServiceImpl(commandQueue)
      val httpApp = Logger.httpApp(true, true)(accountRoutes(accountService).orNotFound)
      
      IO.println("🚀 Account Service is starting on port 8081...") *>

      EmberServerBuilder
        .default[IO]
        .withHost(host"localhost")
        .withPort(port"8081")
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)

    }.as(ExitCode.Success)
    