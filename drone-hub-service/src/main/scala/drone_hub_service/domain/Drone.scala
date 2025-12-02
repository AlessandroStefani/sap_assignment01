package drone_hub_service.domain

import common.ddd.Entity
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

  def deliver(origin: String, destination: String, weight: Double): Unit =

    if (!busy.compareAndSet(false, true)) {
      throw new IllegalStateException(s"Drone $id is already busy!")
    }
    val flightDurationSeconds = ((origin.length + destination.length) * 0.5 * weight).toInt.min(weight.toInt + 15)

    println(s"[DRONE $id] Starting delivery from $origin to $destination. Estimated time: ${flightDurationSeconds}s")

    Future {
      val steps = flightDurationSeconds
      for (i <- 1 to steps) {
        Thread.sleep(1000)
        val mockLat = 44.0 + i.toDouble
        val mockLon = 12.0 + i.toDouble

        sendTelemetryToTrackingService(mockLat, mockLon, flightDurationSeconds - i)
      }

      println(s"[DRONE $id] Delivery completed at $destination!")
      busy.set(false)
    }

  private def sendTelemetryToTrackingService(lat: Double, lon: Double, tta: Int): Unit = {
    println(s"   >>> [TELEMETRY] Drone $id at ($lat, $lon): time until arrival $tta -> Sending to TrackingService...")
    tracker.updateDrone(id, lat, lon, tta)
  }
