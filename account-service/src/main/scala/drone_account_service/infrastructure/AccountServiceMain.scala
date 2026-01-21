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
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import cats.syntax.all.*
import org.http4s.server.middleware.{Logger, Metrics}

object AccountServiceMain extends IOApp:

  private def accountRoutes(service: AccountService): HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req @ POST -> Root / "test" / "login" =>
      req.as[AccountPost].flatMap: input =>
        service.loginUser(input.username, input.password).flatMap: isValid =>
          if isValid then Ok("Login successful")
          else NotFound("Invalid credentials")

    case req @ POST -> Root / "test" / "logout" =>
      req.as[AccountPost].flatMap: input =>
        service.logoutUser(input.username).flatMap: removed =>
          if removed then
            Ok(s"User ${input.username} logged out")
          else
            NotFound("User not logged in")

    case req @ POST -> Root / "test" / "register" =>
      req.as[AccountPost].flatMap: inputData =>
        service.registerUser(inputData.username, inputData.password).flatMap: newAccount =>
          Ok(newAccount)
        .handleErrorWith:
          case e: RuntimeException if e.getMessage.contains("exists") =>
            Conflict(s"Errore: ${e.getMessage}")
          case e =>
            InternalServerError(s"Errore imprevisto: ${e.getMessage}")

    case GET -> Root / "health" => 
      Ok("OK")

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      dbComponents <- FileDatabase.make("data/accounts.json")
      (commandQueue, accountReader) = dbComponents

      service = new AccountServiceImpl(commandQueue, accountReader)

      metricsSvc <- PrometheusExportService.build[IO]
      metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "account_service")

      businessRoutes = accountRoutes(service)
      meteredRoutes = Metrics[IO](metricsOps)(businessRoutes)
      /*httpApp = Logger.httpApp(true, false)((metricsSvc.routes <+> meteredRoutes).orNotFound)*/

      silentHealthRoute = HttpRoutes.of[IO] {
        case GET -> Root / "health" => Ok("OK") 
      }
      silentRoutes = metricsSvc.routes <+> silentHealthRoute
      loggedBusinessRoutes = Logger.httpRoutes(true, false)(meteredRoutes)
      httpApp = (silentRoutes <+> loggedBusinessRoutes).orNotFound

      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8081")
        .withHttpApp(httpApp)
        .build
    yield server

    IO.println("🚀 Account Service is starting...") *>
      appResource.use(_ => IO.never).as(ExitCode.Success)