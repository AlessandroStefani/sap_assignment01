package drone_hub_service.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import drone_hub_service.application.DroneTracking
import drone_hub_service.domain.{DroneId, Order, TrackingUpdate}
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Method, Request, Uri}
import org.http4s.implicits.*

@Adapter
class DroneTrackingProxy extends DroneTracking:

  private val trackingServiceUrl: String = "http://localhost:8083"
  private val baseUri = Uri.fromString(trackingServiceUrl).getOrElse(uri"http://localhost:8083")
  private val endpoint = baseUri / "api" / "tracking" / "update"

  override def updateDrone(id: DroneId, order: Order, lat: Double, lon: Double, tta: Int): IO[Unit] =
    val payload = TrackingUpdate(id.id, order.id.id, lat, lon, tta)
    val request = Request[IO](Method.POST, endpoint).withEntity(payload)

    IO.println(s"📤 [Proxy] INVIO update per drone ${id.id}...") *>
    EmberClientBuilder.default[IO].build.use { client =>
      client.run(request).use { response =>
        if (response.status.isSuccess) then
          IO.println(s"✅ [Proxy] Update inviato con SUCCESSO (Status: ${response.status})")
        else
          IO.println(s"⚠️ [Proxy] Il server ha risposto con ERRORE: ${response.status}")
        IO.unit
      }
    }.handleErrorWith { e =>
      IO.println(s"[Proxy] Errore invio tracking: ${e.getMessage}")
    }