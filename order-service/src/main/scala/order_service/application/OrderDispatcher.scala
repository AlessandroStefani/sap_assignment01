package order_service.application

import cats.effect.IO
import cats.syntax.all.*

import scala.concurrent.duration.*
import order_service.domain.{DroneId, Order}
import java.util.UUID

class OrderDispatcher(repo: OrderRepository, droneHub: DroneHubService):

  def start: IO[Unit] =
    loop.foreverM

  private def loop: IO[Unit] =
    for
      pendingOrders <- repo.getPendingOrders
      _ <- pendingOrders.traverse(processOrder)
      _ <- IO.sleep(3.seconds)
    yield ()

  private def processOrder(order: Order): IO[Unit] =
    val assignedDroneId = DroneId(s"drone-${UUID.randomUUID().toString.take(8)}")
    val orderWithDrone = order.copy(droneId = Some(assignedDroneId))

    (for
      _ <- IO.println(s"[Dispatcher] Processing order ${order.id}...")
      _ <- droneHub.shipOrder(orderWithDrone)
      _ <- repo.updateOrder(order.usrId, orderWithDrone)
      _ <- IO.println(s"[Dispatcher] Order ${order.id} shipped via ${assignedDroneId.id}")
    yield ()).handleErrorWith { e =>
      IO.println(s"[Dispatcher] Error processing order ${order.id}: ${e.getMessage}")
    }
