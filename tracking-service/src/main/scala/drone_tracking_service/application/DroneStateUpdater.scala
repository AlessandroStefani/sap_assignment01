package drone_tracking_service.application

import cats.effect.IO
import common.exagonal.InBoundPort
import drone_tracking_service.domain.DroneTelemetry

@InBoundPort
trait DroneStateUpdater:
  def updateDronePosition(telemetry: DroneTelemetry): IO[Unit]
