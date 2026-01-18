package order_service

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import order_service.application.{OrderServiceImpl, OrderDispatcher}
import order_service.infrastructure.{InMemoryOrderRepoImpl, DroneHubServiceProxy}
import order_service.domain.{NewOrderRequest, Order}
import java.time.Instant

class OrderServiceComponentTest extends AnyFlatSpec with Matchers:
  private val externalServicesStub = HttpRoutes.of[IO] {
    case POST -> Root / "drone" / "dispatch" => Ok("Drone dispatched (Stubbed)")
    case _ => NotFound()
  }.orNotFound
  private val stubClient = Client.fromHttpApp(externalServicesStub)

  private val orderRepo = InMemoryOrderRepoImpl
  val droneHubProxy = new DroneHubServiceProxy(stubClient)
  val orderService = new OrderServiceImpl(orderRepo)
  // giusto per replicare l'architettura reale
  val dispatcher = new OrderDispatcher(orderRepo, droneHubProxy)

  private val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root / "order" / "new" =>
      req.as[NewOrderRequest].flatMap { input =>
        orderService.newOrder(
          input.userId,
          input.origin,
          input.destination,
          input.weight,
          input.departureDate
        ).flatMap { orderId =>
          Accepted(s"Order ${orderId.id} scheduled successfully")
        }
      }

    case GET -> Root / "order" / "user" / userId =>
      orderService.getOrders(userId).flatMap(Ok(_))
  }.orNotFound

  "OrderService Component" should "handle the full lifecycle of an order via API" in:
    val userId = "component-user-01"
    // SCENARIO: user crea ordine -> verifica successo -> verifica presenza in lista
    val newOrderPayload = NewOrderRequest(
      userId = userId,
      weight = 2.5,
      origin = "Store-Central",
      destination = "Home-123",
      departureDate = Instant.now()
    )
    val createRequest = Request[IO](Method.POST, uri"/order/new").withEntity(newOrderPayload)
    val createResponse = routes.run(createRequest).unsafeRunSync()

    createResponse.status shouldBe Status.Accepted

    val getRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/order/user/$userId"))
    val getResponse = routes.run(getRequest).unsafeRunSync()

    getResponse.status shouldBe Status.Ok

    val ordersList = getResponse.as[List[Order]].unsafeRunSync()
    ordersList should have size 1
    ordersList.head.destination shouldBe "Home-123"
