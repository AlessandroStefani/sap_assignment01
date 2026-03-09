package order_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp, Resource}
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
import io.prometheus.client.Counter
import cats.syntax.all.*

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
          Accepted(s"Order ${orderId.id} created and queued for shipment.")
        }
      }.handleErrorWith { e =>
        BadRequest(s"Invalid order request: ${e.getMessage}")
      }

    case GET -> Root / "order" / "user" / userId =>
      service.getOrders(userId).flatMap(Ok(_))

    case GET -> Root / "health" =>
      Ok("OK")

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build
      orderRepo <- FileOrderRepository.make("data/orders.json")

      metricsSvc <- PrometheusExportService.build[IO]

      dispatchCounter <- Resource.eval(IO {
        Counter.build()
          .name("order_dispatcher_processed_total")
          .help("Total orders processed by the background dispatcher")
          .labelNames("status")
          .register(metricsSvc.collectorRegistry)
      })

      publisher = new OrderEventPublisher()
      orderService = new OrderServiceImpl(orderRepo)
      orderDispatcher = new OrderDispatcher(orderRepo, publisher, dispatchCounter)
      _ <- orderDispatcher.start.background

      metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "order_service")

      businessRoutes = routes(orderService)
      meteredRoutes = Metrics[IO](metricsOps)(businessRoutes)

      silentHealthRoute = HttpRoutes.of[IO] { case GET -> Root / "health" => Ok("OK") }
      silentRoutes = metricsSvc.routes <+> silentHealthRoute
      loggedBusinessRoutes = Logger.httpRoutes(true, false)(meteredRoutes)

      httpApp = (silentRoutes <+> loggedBusinessRoutes).orNotFound

      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(ORDER_SERVICE_PORT)
        .withHttpApp(httpApp)
        .build

      consumerStream = DroneIdConsumer.stream(orderRepo)

    yield (server, consumerStream)

    IO.println(s"📦 Order Service starting on $ORDER_SERVICE_PORT (Kafka Consumer Active)...") *>
      appResource.use { case (_, consumerStream) =>
        (IO.never, consumerStream.compile.drain).parTupled
      }.as(ExitCode.Success)