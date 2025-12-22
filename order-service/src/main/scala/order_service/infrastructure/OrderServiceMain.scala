package order_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*
import order_service.application.*
import order_service.domain.NewOrderRequest

import java.time.Instant

object OrderServiceMain extends IOApp:
  private val ORDER_SERVICE_PORT = port"9068"

  private def routes(service: OrderService): HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req @ POST -> Root / "orders" / "new" =>
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

    case GET -> Root / "orders" / "user" / userId =>
      service.getOrders(userId).flatMap(Ok(_))

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build
      droneHubProxy = new DroneHubServiceProxy(client)
      repo = InMemoryOrderRepoImpl
      orderService = new OrderServiceImpl(repo)
      dispatcher = new OrderDispatcher(repo, droneHubProxy)

      httpApp = Logger.httpApp(true, true)(routes(orderService).orNotFound)
      server = EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(ORDER_SERVICE_PORT)
        .withHttpApp(httpApp)
        .build
    yield (server, dispatcher)

    appResource.use { case (_, dispatcher) =>
      (IO.never, dispatcher.start).parTupled.as(ExitCode.Success)
    }