package drone_hub_service.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import drone_hub_service.application.DroneTracking
import drone_hub_service.domain.{DroneId, TrackingUpdate}
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Method, Request, Uri}
import org.http4s.implicits.*

@Adapter
class DroneTrackingProxy(trackingServiceUrl: String = "http://localhost:8083") extends DroneTracking:

  //todo: sistemare gli endpoint
  private val baseUri = Uri.fromString(trackingServiceUrl).getOrElse(uri"http://localhost:8083")
  private val endpoint = baseUri / "api" / "tracking" / "update"

  override def updateDrone(id: DroneId, lat: Double, lon: Double, tta: Int): IO[Unit] =
    val payload = TrackingUpdate(id, lat, lon, tta)
    val request = Request[IO](Method.POST, endpoint).withEntity(payload)

    EmberClientBuilder.default[IO].build.use { client =>
      client.run(request).use { _ => //Ignoro la risposta ma possiamo fare come ci pare
        IO.unit
      }
    }.handleErrorWith { e =>
      IO.println(s"[Proxy] Errore invio tracking: ${e.getMessage}")
    }