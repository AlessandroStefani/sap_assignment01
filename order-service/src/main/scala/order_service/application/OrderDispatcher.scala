package order_service.application

import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*
import order_service.domain.{DroneId, Order}
import io.prometheus.client.Counter

class OrderDispatcher(repo: OrderRepository, droneHub: DroneHubService, metricCounter: Counter):

  def start: IO[Unit] =
    loop.foreverM

  private def loop: IO[Unit] =
    for
      pendingOrders <- repo.getPendingOrders
      _ <- pendingOrders.traverse(processOrder)
      _ <- IO.sleep(3.seconds)
    yield ()

  private def processOrder(order: Order): IO[Unit] =
    val waitingOrder = order.copy(droneId = Some(DroneId("WAITING_FOR_HUB")))

    (for
      _ <- IO.println(s"[Dispatcher] Elaborazione ordine ${order.id} in corso...")

      _ <- droneHub.shipOrder(order)

      _ <- repo.updateOrder(order.usrId, waitingOrder)

      _ <- IO.println(s"[Dispatcher] Ordine ${order.id} inviato al Drone Hub. In attesa del drone...")
      _ <- IO(metricCounter.labels("success").inc())
    yield ()).handleErrorWith { e =>
      // failure metric
      IO(metricCounter.labels("failure").inc()) *>
        IO.println(s"[Dispatcher] Error processing order ${order.id}: ${e.getMessage}")
    }