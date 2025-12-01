package drone_api_gateway.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import drone_api_gateway.domain.AccountPost
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

import scala.language.postfixOps

object APIGateway extends IOApp:
  private val BACKEND_PORT = port"8080"

  private val apiRootVersion = "test"
  
  private var loggedUser: List[String] = List.empty

  private def routes(client: Client[IO]): HttpRoutes[IO] =

    val accountServiceProxy: AccountServiceProxy = AccountServiceProxy(client)

    HttpRoutes.of[IO]:
      case req @ POST -> Root / apiRootVersion / "login" =>
        req.as[AccountPost].flatMap:
            login =>
              accountServiceProxy.loginUser(login.username, login.password).flatMap:
                isLogged =>
                  if isLogged then 
                    loggedUser = login.username :: loggedUser
                    Ok("Login effettuato")
                  else NotFound("errore nel login, account non trovato o credenziali sbagliate")
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
          
          if error.getMessage == "gia loggato" then Found(error.getMessage)
          else
            ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di login. Causa: ${error.getMessage}")

      case req @ POST -> Root / apiRootVersion / "register" =>
        req.as[AccountPost].flatMap:
          account =>
            accountServiceProxy.registerUser(account.username, account.password).flatMap:
              res => Created(res)
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
          ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di login. Causa: ${error.getMessage}")

      case req @ POST -> Root / apiRootVersion / "trackOrder" => ???
        
      case _ => Ok("api not found")

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build
      httpApp = Logger.httpApp(true, true)(routes(client).orNotFound)

      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(BACKEND_PORT)
        .withHttpApp(httpApp)
        .build
    yield server


    appResource
      .use(_ => IO.never)
      .as(ExitCode.Success)

