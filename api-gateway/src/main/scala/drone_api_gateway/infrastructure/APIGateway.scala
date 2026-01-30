package drone_api_gateway.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import drone_api_gateway.application.{LoginErrorException, NotLoggedException}
import drone_api_gateway.domain.tracking.TrackingRequest
import drone_api_gateway.domain.{AccountPost, NewOrderRequest}
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.middleware.{Logger, Metrics}
import org.http4s.{HttpRoutes, Response, ResponseCookie, SameSite}
import cats.syntax.all.*

import scala.language.postfixOps

object APIGateway extends IOApp:
  private val BACKEND_PORT = port"8080"

  private val apiRootVersion = "test"
  

  private val COOKIE_NAME = "drone_auth_session"

  private def handleClientError(serviceName: String)(error: Throwable): IO[Response[IO]] =
    IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
      ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di $serviceName. Causa: ${error.getMessage}")

  private def routes(client: Client[IO]): HttpRoutes[IO] =

    val accountServiceProxy: AccountServiceProxy = AccountServiceProxy(client)
    val orderServiceProxy = OrderServiceProxy(client)
    val trackingServiceProxy = TrackingServiceProxy(client)

    HttpRoutes.of[IO]:
      case req @ POST -> Root / apiRootVersion / "login" =>
        req.as[AccountPost].flatMap:
            login =>
              accountServiceProxy.loginUser(login.username, login.password).flatMap:
                isLogged =>
                  if isLogged then 

                    val authCookie = ResponseCookie(
                      name = COOKIE_NAME,
                      content = login.username,
                      httpOnly = true,
                      secure = false,
                      path = Some("/"),
                      sameSite = Some(SameSite.Strict)
                    )

                    Ok("Login effettuato")map(_.addCookie(authCookie))
                  else
                    throw new LoginErrorException
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
          
          if error.getMessage == "gia loggato" then Found(error.getMessage)
          else
            handleClientError("login")(error)

      case req @ POST -> Root / apiRootVersion / "register" =>
        req.as[AccountPost].flatMap:
          account =>
            accountServiceProxy.registerUser(account.username, account.password).flatMap:
              res => Created(res)
        .handleErrorWith: error =>
          handleClientError("registrazione")(error)

      case req @ POST -> Root / apiRootVersion / "orders" =>
        req.as[NewOrderRequest].flatMap: orderDto =>

          val userFromCookie = req.cookies.find(_.name == COOKIE_NAME).map(_.content)

          userFromCookie match
            case Some(username) =>
              orderServiceProxy.placeOrder(
                username,
                orderDto.origin,
                orderDto.destination,
                orderDto.weight,
                orderDto.departureDate
              ).flatMap(res => Accepted(res))
            case None =>
              throw new NotLoggedException()
        .handleErrorWith: error =>
          handleClientError("creazione ordine")(error)

      case req @ GET -> Root / apiRootVersion / "orders" =>
        val userFromCookie = req.cookies.find(_.name == COOKIE_NAME).map(_.content)

        userFromCookie match
          case Some(username) =>
            orderServiceProxy.getOrders(username)
              .flatMap(orders => Ok(orders))
              .handleErrorWith(error => handleClientError("recupero ordini")(error))
          case None =>
            handleClientError("recupero ordini")(NotLoggedException())

      case req @ POST -> Root / apiRootVersion / "trackOrder" =>
        req.as[TrackingRequest].flatMap: trackOrder =>
          val userFromCookie = req.cookies.find(_.name == COOKIE_NAME).map(_.content)

          if userFromCookie.isDefined then
            trackingServiceProxy.trackDrone(TrackingRequest(trackOrder.orderId, trackOrder.droneId)).flatMap: res =>
              Ok(res)
          else
            throw new NotLoggedException()
              
        .handleErrorWith: error =>
          handleClientError("tracciamento ordine")(error)

      case req @ POST -> Root / apiRootVersion / "logout" =>
        val userToLogout = req.cookies.find(_.name == COOKIE_NAME).map(_.content)

        if userToLogout.isDefined then

          val clearCookie = ResponseCookie(
            name = COOKIE_NAME,
            content = "",
            httpOnly = true,
            secure = false,
            path = Some("/"),
            maxAge = Some(0),
            sameSite = Some(SameSite.Strict)
          )

          accountServiceProxy.logoutUser(userToLogout.get).flatMap: result =>
            if result then
              Ok("Logout effettuato correttamente").map(_.addCookie(clearCookie))
            else
              Ok("Nessun utente era loggato").map(_.addCookie(clearCookie))
          .handleErrorWith: error =>
            handleClientError("logout")(error)
        else
          Ok("Nessun utente era loggato")

      case GET -> Root / "health" =>
        Ok("{\"status\": \"UP\"}")

      case _ => Ok("api not found")

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build

      baseRoutes = routes(client)

      metricsSvc <- PrometheusExportService.build[IO]
      metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "api_gateway")

      meteredRoutes = Metrics[IO](metricsOps)(baseRoutes)

      /*httpApp = Logger.httpApp(true, false)((metricsSvc.routes <+> meteredRoutes).orNotFound)*/

      silentHealthRoute = HttpRoutes.of[IO] { 
        case GET -> Root / "health" => Ok("{\"status\": \"UP\"}") 
      }
      silentRoutes = metricsSvc.routes <+> silentHealthRoute
      loggedBusinessRoutes = Logger.httpRoutes(true, false)(meteredRoutes)
      httpApp = (silentRoutes <+> loggedBusinessRoutes).orNotFound

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
