package order_service.application

import cats.effect.IO
import order_service.domain.{DroneId, Order, OrderId}
import java.time.Instant
import java.util.UUID

class OrderServiceImpl(repo: OrderRepository, droneHub: DroneHubService) extends OrderService:

  override def newOrder(userId: String, origin: String, destination: String, weight: Double): IO[DroneId] =
    val orderId = OrderId(UUID.randomUUID().toString)
    val preOrder = Order(
      id = orderId,
      usrId = userId,
      weight = weight,
      origin = origin,
      destination = destination,
      departureDate = Instant.now(),
      droneId = None
    )

    for
      assignedDroneId <- droneHub.shipOrder(preOrder)
      finalOrder = preOrder.copy(droneId = Some(assignedDroneId))
      _ <- IO(repo.addOrder(userId, finalOrder))
      _ <- IO.println(s"Ordine $orderId creato e assegnato al drone $assignedDroneId")
    yield assignedDroneId

  override def getOrders(userId: String): IO[List[Order]] =
    IO(repo.getUserOrders(userId))