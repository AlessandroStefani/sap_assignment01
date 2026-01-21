package order_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.{Logger, Metrics}
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*
import order_service.application.*
import order_service.domain.NewOrderRequest
import java.time.Instant
import scala.language.postfixOps
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}

object OrderServiceMain extends IOApp:
  private val ORDER_SERVICE_PORT = port"9068"

  private def routes(service: OrderService): HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req @ POST -> Root / "order" / "new" =>
      req.as[NewOrderRequest].flatMap { input =>
        service.newOrder(
          input.userId,
          input.origin,
          input.destination,
          input.weight,
          input.departureDate
        ).flatMap { orderId =>
          Accepted(s"Order ${orderId.id} scheduled successfully for ${input.departureDate}")
        }
      }.handleErrorWith { e =>
        BadRequest(s"Invalid order request: ${e.getMessage}")
      }

    case GET -> Root / "order" / "user" / userId =>
      service.getOrders(userId).flatMap(Ok(_))

    case GET -> Root / "health" =>
      Ok("OK")

  override def run(args: List[String]): IO[ExitCode] =
    EmberClientBuilder.default[IO].build.use { client =>
      val orderRepo = InMemoryOrderRepoImpl
      val droneHub = new DroneHubServiceProxy(client)

      val orderService = new OrderServiceImpl(orderRepo)
      val dispatcher = new OrderDispatcher(orderRepo, droneHub)

      //val httpApp = Logger.httpApp(true, true)(routes(orderService).orNotFound)

      val appResource = for
        metricsSvc <- PrometheusExportService.build[IO]
        metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "order_service")

        businessRoutes = routes(orderService)
        meteredRoutes = Metrics[IO](metricsOps)(businessRoutes)

        /*httpApp = Logger.httpApp(true, false)((metricsSvc.routes <+> meteredRoutes).orNotFound)*/

        silentHealthRoute = HttpRoutes.of[IO] {
          case GET -> Root / "health" => Ok("OK") 
        }
        silentRoutes = metricsSvc.routes <+> silentHealthRoute
        loggedBusinessRoutes = Logger.httpRoutes(true, false)(meteredRoutes)
        httpApp = (silentRoutes <+> loggedBusinessRoutes).orNotFound

        _ <- dispatcher.start.background
        server <- EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(ORDER_SERVICE_PORT)
          .withHttpApp(httpApp)
          .build
      yield server

      IO.println(s"📦 Order Service starting on $ORDER_SERVICE_PORT...") *>
        IO.println(s"🔄 Dispatcher started inside background resource...") *>
        appResource.use(_ => IO.never)
    }.as(ExitCode.Success)