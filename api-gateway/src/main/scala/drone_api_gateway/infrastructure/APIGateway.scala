package drone_api_gateway.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.uri
import org.http4s.server.middleware.Logger

import scala.language.postfixOps

case class AccountServiceCommand(username: String, password: String)

object APIGateway extends IOApp:
  private val BACKEND_PORT = port"8080"

  private val authServiceUrl = uri"http://localhost:8081/test/login"

  private def routes(client: Client[IO]): HttpRoutes[IO] =

    val accountServiceProxy: AccountServiceProxy = AccountServiceProxy(client)


    HttpRoutes.of[IO]:
      case req @ POST -> Root / "test" / "login" =>
        req.as[AccountServiceCommand].flatMap:
            login =>
              accountServiceProxy.loginUser(login.username, login.password).flatMap:
                isLogged =>
                  if isLogged then Ok("Login effettuato")
                  else Ok("errore nel login")
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
          ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di login. Causa: ${error.getMessage}")

      case req @ POST -> Root / "test" / "register" =>
        req.as[AccountServiceCommand].flatMap:
          account =>
            accountServiceProxy.registerUser(account.username, account.password).flatMap:
              res => Created(res)
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
          ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di login. Causa: ${error.getMessage}")

      case _ => Ok("api not found")

  //private def loggedRoutes(client: Client[IO]): HttpApp[IO] = Logger.httpApp(true, true)(routes(client).orNotFound)

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build
      //httpApp = loggedRoutes(client)
      httpApp = Logger.httpApp(true, true)(routes(client).orNotFound)

      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"localhost")
        .withPort(BACKEND_PORT)
        .withHttpApp(httpApp)
        .build
    yield server


    appResource
      .use(_ => IO.never)
      .as(ExitCode.Success)

