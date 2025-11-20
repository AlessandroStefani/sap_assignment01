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

    // Login
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

    // Register
    case req @ POST -> Root / "test" / "register" =>
      req.as[AccountPost].flatMap { inputData =>

        // Chiamiamo il servizio vero (che metterà il messaggio in coda)
        service.registerUser(inputData.name, inputData.password).flatMap { newAccount =>
          // Se va bene, restituiamo l'account creato (200 OK)
          Ok(newAccount)
        }.handleErrorWith {
          // Gestione errori specifica (es. utente già esistente)
          case e: RuntimeException if e.getMessage.contains("exists") =>
            Conflict(s"Errore: ${e.getMessage}") // 409 Conflict
          case e =>
            InternalServerError(s"Errore imprevisto: ${e.getMessage}")
        }
      }

    case _ => NotFound("Rotta non trovata")
  }

  // 2. METODO RUN (IL CABLAGGIO)
  def run(args: List[String]): IO[ExitCode] =

    // A. Creiamo il motore di persistenza (File + Coda + Actor Loop)
    // "accounts.json" è il file dove verranno salvati i dati
    FileDatabase.make("src/main/resources/accounts.json").use { commandQueue =>

      // B. Creiamo manualmente il Servizio passandogli la Coda
      val accountService = new AccountServiceImpl(commandQueue)

      // C. Configuriamo l'App HTTP iniettando il servizio nelle rotte
      val httpApp = Logger.httpApp(true, true)(accountRoutes(accountService).orNotFound)

      IO.println("🚀 Account Service is starting on port 8081...") *>

      // D. Avviamo il Server
      EmberServerBuilder
        .default[IO]
        .withHost(host"localhost")
        .withPort(port"8081")
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)

    }.as(ExitCode.Success)