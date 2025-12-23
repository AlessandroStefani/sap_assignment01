package drone_api_gateway.infrastructure

import cats.effect.IO
import drone_api_gateway.application.TrackingService
import drone_api_gateway.domain.tracking.{DroneTelemetry, TrackingRequest}
import org.http4s.client.Client
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

class TrackingServiceProxy(client: Client[IO]) extends TrackingService:

  private val trackingServiceUrl = uri"http://localhost:8083/tracking/trackDrone"
  private val dockerUri = uri"http://tracking-service:8083/tracking/trackDrone"

  override def trackDrone(trackingRequest: TrackingRequest): IO[DroneTelemetry] =
    val requestBody = trackingRequest

    val request = Request[IO](method = Method.POST, dockerUri).withEntity(requestBody)

    client.expect[DroneTelemetry](request).handleErrorWith { e =>
      IO.println(s"⚠️ Errore ${e.getMessage}") *>
        //IO.pure(DroneTelemetry("errore", "errore", 0.0, 0.0, 0))
        IO.raiseError(new RuntimeException(s"Errore comunicazione con TrackingService: ${e.getMessage}"))
    }
