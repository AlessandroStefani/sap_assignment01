package drone_tracking_service.application

import cats.effect.IO
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

trait TrackingService:
  //def updateDronePosition(telemetry: DroneTelemetry): IO[Unit]
  def trackDrone(request: TrackingRequest): IO[DroneTelemetry]