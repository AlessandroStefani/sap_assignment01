package drone_tracking_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.comcast.ip4s.*
import drone_tracking_service.application.TrackingServiceImpl
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.middleware.{Logger, Metrics}
import org.http4s.{HttpRoutes, Response}

object TrackingServiceMain extends IOApp:

  private val BACKEND_PORT = port"8083"

  private def routes(trackingService: TrackingServiceImpl): HttpRoutes[IO] =
    HttpRoutes.of[IO]:
      case req @ POST -> Root / "api" / "tracking" / "update" =>
        req.as[DroneTelemetry].flatMap { telemetry =>
          IO.println(s"📡 [TrackingService] RICEVUTO telemetry: $telemetry") *>
          trackingService.updateDronePosition(telemetry) *> Ok()
        }.handleErrorWith { error =>
          IO.println(s"Errore update telemetry: ${error.getMessage}") *>
            BadRequest(s"Invalid data: ${error.getMessage}")
        }

      case req @ POST -> Root / "tracking" / "trackDrone" =>
        req.as[TrackingRequest].flatMap { trackRequest =>
          trackingService.trackDrone(trackRequest).flatMap { trackingInfo =>
            Ok(trackingInfo)
          }
        }.handleErrorWith { error =>
          IO.println(s"Errore tracking client: ${error.getMessage}") *>
            NotFound(s"Tracking info not found or mismatch: ${error.getMessage}")
        }

      case GET -> Root / "health" =>
        Ok("OK")

      case _ => NotFound("Rotta non trovata")

  override def run(args: List[String]): IO[ExitCode] =
    val dbResource = FileEventStore.make("data/tracking-events.json")

    val appResource = for
      dbComponents <- dbResource
      (eventStore, stateRef) = dbComponents

      trackingService = new TrackingServiceImpl(stateRef, eventStore)

      metricsSvc <- PrometheusExportService.build[IO]
      metricsOps <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "tracking_service")

      businessRoutes = routes(trackingService)
      meteredRoutes = Metrics[IO](metricsOps)(businessRoutes)
      /*httpApp = Logger.httpApp(true, false)((metricsSvc.routes <+> meteredRoutes).orNotFound)*/
      
      silentHealthRoute = HttpRoutes.of[IO] {
        case GET -> Root / "health" => Ok("OK") 
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

    IO.println("🚀 Tracking Service is starting...") *>
      appResource.use(_ => IO.never).as(ExitCode.Success)