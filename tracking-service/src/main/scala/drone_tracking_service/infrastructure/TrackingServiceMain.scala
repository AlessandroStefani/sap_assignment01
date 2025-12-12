package drone_tracking_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import drone_tracking_service.application.{TrackingService, TrackingServiceImpl}
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, Response}

object TrackingServiceMain extends IOApp:

  private val BACKEND_PORT = port"8083"

  private def routes(trackingService: TrackingService): HttpRoutes[IO] =
    HttpRoutes.of[IO]:
      // 1. Endpoint per ricevere aggiornamenti dal drone (usato da drone-hub-service)
      case req @ POST -> Root / "api" / "tracking" / "update" =>
        req.as[DroneTelemetry].flatMap { telemetry =>
          trackingService.updateDronePosition(telemetry) *> Ok()
        }.handleErrorWith { error =>
          IO.println(s"Errore update telemetry: ${error.getMessage}") *>
            BadRequest(s"Invalid data: ${error.getMessage}")
        }

      // 2. Endpoint per il client (API Gateway o utente finale)
      case req @ POST -> Root / "trackDrone" =>
        req.as[TrackingRequest].flatMap { trackRequest =>
          trackingService.trackDrone(trackRequest).flatMap { trackingInfo =>
            Ok(trackingInfo)
          }
        }.handleErrorWith { error =>
          IO.println(s"Errore tracking client: ${error.getMessage}") *>
            NotFound(s"Tracking info not found or mismatch: ${error.getMessage}")
        }

  override def run(args: List[String]): IO[ExitCode] =
    TrackingServiceImpl.create.flatMap { trackingService =>
      val httpApp = Logger.httpApp(true, true)(routes(trackingService).orNotFound)

      EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(BACKEND_PORT)
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)
    }