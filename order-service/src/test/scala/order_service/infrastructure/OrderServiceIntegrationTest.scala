package order_service.infrastructure

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto.*
import order_service.application.OrderServiceImpl
import order_service.domain.{NewOrderRequest, Order}
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class OrderServiceIntegrationTest extends AnyFlatSpec with Matchers:
  private val realRepo = InMemoryOrderRepoImpl
  private val realService = new OrderServiceImpl(realRepo)

  private val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root / "order" / "new" =>
      req.as[NewOrderRequest].flatMap { input =>
        realService.newOrder(
          input.userId,
          input.origin,
          input.destination,
          input.weight,
          input.departureDate
        ).flatMap { orderId =>
          Accepted(s"Order ${orderId.id} scheduled successfully for ${input.departureDate}")
        }
      }.handleErrorWith { e =>
        BadRequest(s"Invalid order request: ${e.getMessage}")
      }

    case GET -> Root / "order" / "user" / userId =>
      realService.getOrders(userId).flatMap(Ok(_))
  }.orNotFound

  "OrderRoutes" should "accept a valid POST request and persist the order" in:
    val userId = "integration-user-1"
    val departureDate = Instant.parse("2025-11-02T10:00:00Z")

    val newOrderRequest = NewOrderRequest(
      userId = userId,
      weight = 12.5,
      origin = "Depot-A",
      destination = "Customer-B",
      departureDate = departureDate
    )
    val request = Request[IO](Method.POST, uri"/order/new").withEntity(newOrderRequest)
    val response = routes.run(request).unsafeRunSync()

    response.status shouldBe Status.Accepted

    val storedOrders = realRepo.getUserOrders(userId).unsafeRunSync()
    storedOrders should have size 1
    val storedOrder = storedOrders.head

    storedOrder.usrId shouldBe userId
    storedOrder.destination shouldBe "Customer-B"
    storedOrder.departureDate shouldBe departureDate

  it should "retrieve orders via GET request" in:
    val userId = "integration-user-2"
    realService.newOrder(userId, "A", "B", 5.0, Instant.now()).unsafeRunSync()

    val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/order/user/$userId"))
    val response = routes.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok

    val ordersList = response.as[List[Order]].unsafeRunSync()
    ordersList should have size 1
    ordersList.head.usrId shouldBe userId
