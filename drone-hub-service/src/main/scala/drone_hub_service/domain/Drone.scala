package drone_hub_service.domain

import common.ddd.Entity
import drone_hub.domain.OrderId
import drone_hub_service.infrastructure.DroneTrackingProxy

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

// Ogni drone potrebbe essere un micro-servizio a se stante.

case class Drone(id: DroneId) extends Entity[DroneId]:
  private val busy = new AtomicBoolean(false)
  private val tracker = new DroneTrackingProxy()

  override def getId: DroneId = id

  def isAvailable: Boolean = !busy.get()

  def deliver(order: Order): Unit =
    val updatedOrder = order.copy(droneId = Some(this.id))

    if (!busy.compareAndSet(false, true)) {
      throw new IllegalStateException(s"Drone $id is already busy!")
    }
    //random bullshit, GO!!!
    val flightDurationSeconds = ((order.origin.length + order.destination.length) * 0.5 * order.weight).toInt.min(order.weight.toInt + 15)

    println(s"[DRONE $id] Starting delivery from $order.origin to $order.destination. Estimated time: ${flightDurationSeconds}s")

    Future {
      val steps = flightDurationSeconds
      for (i <- 1 to steps) {
        Thread.sleep(1000)
        val mockLat = 44.0 + i.toDouble
        val mockLon = 12.0 + i.toDouble

        sendTelemetryToTrackingService(updatedOrder, mockLat, mockLon, flightDurationSeconds - i)
      }

      println(s"[DRONE $id] Delivery completed at $order.destination!")
      busy.set(false)
    }

  private def sendTelemetryToTrackingService(order: Order, lat: Double, lon: Double, tta: Int): Unit = {
    println(s"   >>> [TELEMETRY] Drone $id at ($lat, $lon): time until arrival $tta -> Sending to TrackingService...")
    tracker.updateDrone(id, order, lat, lon, tta)
  }
