package drone_api_gateway.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import drone_api_gateway.application.OrderService
import drone_api_gateway.domain.{Order, NewOrderRequest}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.http4s.implicits.uri
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

import java.time.Instant

@Adapter
class OrderServiceProxy(client: Client[IO]) extends OrderService:

  private val baseUri = uri"http://localhost:9068"
  private val endpoint = baseUri / "order" / "new"

  private val dockerUri = uri"http://order-service:9068/order/new"

  override def placeOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant): IO[String] =
    val payload = NewOrderRequest(userId, origin, destination, weight, departureDate)
    val request = Request[IO](Method.POST, dockerUri).withEntity(payload)

    client.expect[String](request).handleErrorWith { e =>
      IO.raiseError(new RuntimeException(s"Errore comunicazione con OrderService: ${e.getMessage}"))
    }

  override def getOrders(userId: String): IO[List[Order]] =
    val endpoint = baseUri / "order" / "user" / userId
    val dockerUri = uri"http://order-service:9068/order/user" / userId
    val request = Request[IO](Method.GET, dockerUri)

    client.expect[List[Order]](request).handleErrorWith { e =>
      IO.println(s"⚠️ Errore recupero ordini per $userId: ${e.getMessage}") *>
        IO.pure(List.empty[Order])
    }