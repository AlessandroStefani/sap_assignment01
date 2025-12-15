package drone_hub_service.domain

import cats.effect.IO
import cats.implicits.*
import common.ddd.Entity
import drone_hub_service.infrastructure.DroneTrackingProxy

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.*

case class Drone(id: DroneId) extends Entity[DroneId]:
  private val busy = new AtomicBoolean(false)
  private val tracker = new DroneTrackingProxy()

  override def getId: DroneId = id

  def isAvailable: Boolean = !busy.get()

  def deliver(order: Order): IO[Unit] =
    val updatedOrder = order.copy(droneId = Some(this.id))

    IO(busy.compareAndSet(false, true)).flatMap { available =>
      if (!available) {
        IO.raiseError(new IllegalStateException(s"Drone $id is already busy!"))
      } else {
        val flightDurationSeconds = ((order.origin.length + order.destination.length) * 0.5 * order.weight).toInt.min(order.weight.toInt + 15)

        val deliveryLogic = for {
          _ <- IO.println(s"[DRONE $id] Starting delivery from $order.origin to $order.destination. Estimated time: ${flightDurationSeconds}s")
          _ <- (1 to flightDurationSeconds).toList.traverse_ { i =>
            for {
              _ <- IO.sleep(1.second)
              mockLat = 44.0 + i.toDouble
              mockLon = 12.0 + i.toDouble
              _ <- sendTelemetryToTrackingService(updatedOrder, mockLat, mockLon, flightDurationSeconds - i)
            } yield ()
          }
          _ <- IO.println(s"[DRONE $id] Delivery completed at $order.destination!")
          _ <- IO(busy.set(false))
        } yield ()

        deliveryLogic.start.void
      }
    }

  private def sendTelemetryToTrackingService(order: Order, lat: Double, lon: Double, tta: Int): IO[Unit] = {
    IO.println(s"   >>> [TELEMETRY] Drone $id at ($lat, $lon): time until arrival $tta") *>
      tracker.updateDrone(id, order, lat, lon, tta)
  }
