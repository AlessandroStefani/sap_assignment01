package drone_hub_service.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import drone_hub_service.application.DroneStateUpdater
import drone_hub_service.domain.{DroneId, Order, TrackingUpdate}
import io.circe.generic.auto.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.client.Client
import org.http4s.implicits.*
import org.http4s.{EntityDecoder, Method, Request, Uri}

@Adapter
class DroneTrackingProxy(client: Client[IO]) extends DroneStateUpdater:
  private val baseUri = uri"http://localhost:8083"
  private val endpoint = baseUri / "api" / "tracking" / "update"

  override def updateDrone(id: DroneId, order: Order, lat: Double, lon: Double, tta: Int): IO[Unit] =
    val payload = TrackingUpdate(id.id, order.id.id, lat, lon, tta)
    val request = Request[IO](Method.POST, endpoint).withEntity(payload)

    client.expect[Unit](request)(EntityDecoder.void[IO])
      .flatMap(_ => IO.println(s"✅ [Proxy] Update inviato per drone ${id.id}"))
      .handleErrorWith { e =>
        IO.println(s"⚠️ [Proxy] Errore invio tracking drone ${id.id}: ${e.getMessage}")
      }