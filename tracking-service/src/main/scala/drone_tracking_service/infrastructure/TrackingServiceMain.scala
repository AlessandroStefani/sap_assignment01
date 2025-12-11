package drone_tracking_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import drone_tracking_service.application.{TrackingService, TrackingServiceImpl}
import drone_tracking_service.domain.TrackingRequest
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, Response}

object TrackingServiceMain extends IOApp:
  
  private val BACKEND_PORT = port"8082"
  
  private def routes(): HttpRoutes[IO] =

    val trackingService: TrackingService = TrackingServiceImpl()

    HttpRoutes.of[IO]:
      case req @ POST -> Root / "trackDrone" =>
        req.as[TrackingRequest].flatMap:
          trackRequest =>
            trackingService.trackDrone(trackRequest.orderId).flatMap:
              trackingInfo => Ok(trackingInfo)
        .handleErrorWith: error =>
          IO.println(s"ERRORE CLIENT: ${error.getMessage}") *>
            ServiceUnavailable(s"Gateway Error: impossibile contattare il servizio di tracking. Causa: ${error.getMessage}")

  override def run(args: List[String]): IO[ExitCode] =
    val httpApp = Logger.httpApp(true, true)(routes().orNotFound)
    
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(BACKEND_PORT)
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)

      .as(ExitCode.Success)
