package order_service.application

import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*
import order_service.domain.Order

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
    (for
      _ <- IO.println(s"[Dispatcher] Processing order ${order.id}...")
      droneId <- droneHub.shipOrder(order)
      updatedOrder = order.copy(droneId = Some(droneId))
      _ <- repo.updateOrder(order.usrId, updatedOrder)

      _ <- IO.println(s"[Dispatcher] Order ${order.id} shipped via drone ${droneId.id}")
    yield ()).handleErrorWith { e =>
      IO.println(s"[Dispatcher] Error processing order ${order.id}: ${e.getMessage}")
    }