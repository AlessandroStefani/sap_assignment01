package order_service.application

import cats.effect.IO
import order_service.domain.{DroneId, Order, OrderId}
import java.time.Instant
import java.util.UUID

class OrderServiceImpl(repo: OrderRepository) extends OrderService:

  override def newOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant): IO[OrderId] =
    val orderId = OrderId(UUID.randomUUID().toString)
    val newOrder = Order(
      id = orderId,
      usrId = userId,
      weight = weight,
      origin = origin,
      destination = destination,
      departureDate = departureDate,
      droneId = None
    )

    for
      _ <- repo.addOrder(userId, newOrder)
      _ <- IO.println(s"Ordine $orderId creato e programmato per: $departureDate")
    yield orderId

  override def getOrders(userId: String): IO[List[Order]] =
    repo.getUserOrders(userId)