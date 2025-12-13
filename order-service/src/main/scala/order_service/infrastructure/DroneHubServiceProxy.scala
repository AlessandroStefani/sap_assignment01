package order_service.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import order_service.application.DroneHubService
import order_service.domain.{DroneId, DroneOrderRequest, Order}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.http4s.implicits.uri
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

@Adapter
class DroneHubServiceProxy(client: Client[IO]) extends DroneHubService:

  private val baseUri = uri"http://localhost:9067"
  private val endpoint = baseUri / "drone_hub" / "delivery"

  override def shipOrder(order: Order): IO[DroneId] =
    val payload = DroneOrderRequest(order)
    val request = Request[IO](Method.POST, endpoint).withEntity(payload)

    client.expect[DroneId](request)

