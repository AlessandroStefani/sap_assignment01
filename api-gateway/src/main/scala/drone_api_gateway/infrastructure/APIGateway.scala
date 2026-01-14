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
import org.http4s.{HttpRoutes, Response}
import cats.syntax.all.*

import scala.language.postfixOps

object APIGateway extends IOApp:
  private val BACKEND_PORT = port"8080"

  private val apiRootVersion = "test"
  
  private var loggedUser: Option[String] = Option.empty

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
                    loggedUser = Some(login.username)
                    Ok("Login effettuato")
                  else
                    //NotFound("errore nel login, account non trovato o credenziali sbagliate")
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
          if loggedUser.isDefined then
            orderServiceProxy.placeOrder(
              loggedUser.get,
              orderDto.origin,
              orderDto.destination,
              orderDto.weight,
              orderDto.departureDate
            ).flatMap(res => Accepted(res))
          else
            throw new NotLoggedException()
        .handleErrorWith: error =>
          handleClientError("creazione ordine")(error)

      case GET -> Root / apiRootVersion / "orders" =>
        if loggedUser.isDefined then
          orderServiceProxy.getOrders(loggedUser.get)
            .flatMap(orders => Ok(orders))
            .handleErrorWith(error => handleClientError("recupero ordini")(error))
        else
          handleClientError("recupero ordini")(NotLoggedException())

      case req @ POST -> Root / apiRootVersion / "trackOrder" =>
        req.as[TrackingRequest].flatMap:
          trackOrder =>
            if loggedUser.isDefined then
              trackingServiceProxy.trackDrone(TrackingRequest(trackOrder.orderId, trackOrder.droneId)).flatMap:
                res => Ok(res)
            else
              throw new NotLoggedException()
              
        .handleErrorWith: error =>
          handleClientError("tracciamento ordine")(error)

      case POST -> Root / apiRootVersion / "logout" =>
        if loggedUser.isDefined then
          val userToLogout = loggedUser.get
          accountServiceProxy.logoutUser(userToLogout).flatMap: result =>
            if result then
              loggedUser = Option.empty
              Ok("Logout effettuato correttamente")
            else Ok("Nessun utente era loggato")
          .handleErrorWith: error =>
            handleClientError("logout")(error)
        else
          Ok("Nessun utente era loggato")
      case _ => Ok("api not found")

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build

      baseRoutes = routes(client)

      metricsSvc <- PrometheusExportService.build[IO]
      metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "api_gateway")

      meteredRoutes = Metrics[IO](metricsOps)(baseRoutes)

      httpApp = Logger.httpApp(true, true)((metricsSvc.routes <+> meteredRoutes).orNotFound)

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
