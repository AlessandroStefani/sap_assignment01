package order_service.application

import cats.effect.IO
import order_service.domain.{DroneId, Order, OrderId}
import java.time.Instant
import java.util.UUID

class OrderServiceImpl(repo: OrderRepository, dispatcher: DroneHubService) extends OrderService:

  override def newOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant): IO[OrderId] =
    val orderId = OrderId(UUID.randomUUID().toString)
    val assignedDroneId = DroneId(s"drone-${UUID.randomUUID().toString.take(8)}")
    val newOrder = Order(orderId, userId, weight, origin, destination, departureDate, Some(assignedDroneId))

    for
      _ <- repo.addOrder(userId, newOrder)
      // This now publishes to Kafka instead of calling HTTP
      _ <- dispatcher.shipOrder(newOrder)
      _ <- IO.println(s"Order $orderId created and event published to Kafka")
    yield orderId

  override def getOrders(userId: String): IO[List[Order]] =
    repo.getUserOrders(userId)