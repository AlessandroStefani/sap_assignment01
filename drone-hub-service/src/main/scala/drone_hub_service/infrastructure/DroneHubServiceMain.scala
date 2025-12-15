package drone_hub_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.*
import drone_hub_service.application.{DroneHubService, DroneHubServiceImpl}
import drone_hub_service.domain.DroneOrderRequest
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

object DroneHubServiceMain extends IOApp:
  private val DRONEHUB_PORT = port"9067"

  private def routes(service: DroneHubService): HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req @ POST -> Root / "drone_hub" / "delivery" =>
      req.as[DroneOrderRequest].flatMap { input =>
        service.shipOrder(input.order).flatMap { assignedDroneId =>
          Ok(assignedDroneId)
        }
      }.handleErrorWith {
        case e: RuntimeException => Conflict(s"Impossibile assegnare ordine: ${e.getMessage}")
        case e => InternalServerError(s"Errore imprevisto: ${e.getMessage}")
      }

  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build

      trackingProxy = new DroneTrackingProxy(client)
      droneHubService = new DroneHubServiceImpl(trackingProxy)
      
      httpApp = Logger.httpApp(true, true)(routes(droneHubService).orNotFound)
      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(DRONEHUB_PORT)
        .withHttpApp(httpApp)
        .build
    yield server
    
    IO.println(s"🚁 Drone Hub Service is starting on port $DRONEHUB_PORT...") *>
      appResource.use(_ => IO.never).as(ExitCode.Success)