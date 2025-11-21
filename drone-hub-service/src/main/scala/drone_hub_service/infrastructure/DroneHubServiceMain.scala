package drone_hub_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import drone_hub_service.application.{DroneHubService, DroneHubServiceImpl}
import drone_hub_service.domain.DroneOrderRequest
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

object DroneHubServiceMain extends IOApp:
  private val DRONEHUB_PORT = port"9067"

  private val droneHubService: DroneHubService = new DroneHubServiceImpl()

  private def routes(service: DroneHubService): HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req@POST -> Root / "drone_hub" / "delivery" =>
      req.as[DroneOrderRequest].flatMap: input =>
        IO(service.newOrder(input.origin, input.destination, input.weight)).flatMap: assignedDroneId =>
          Ok(assignedDroneId)
        .handleErrorWith:
          case e: RuntimeException =>
            Conflict(s"Impossibile assegnare ordine: ${e.getMessage}")
          case e =>
            InternalServerError(s"Errore imprevisto: ${e.getMessage}")

  override def run(args: List[String]): IO[ExitCode] =
    val httpApp = Logger.httpApp(true, true)(routes(droneHubService).orNotFound)

    IO.println(s"🚁 Drone Hub Service is starting on port $DRONEHUB_PORT...") *>
      EmberServerBuilder
        .default[IO]
        .withHost(host"localhost")
        .withPort(DRONEHUB_PORT)
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)

