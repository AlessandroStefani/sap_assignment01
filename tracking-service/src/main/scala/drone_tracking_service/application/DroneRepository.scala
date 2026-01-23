package drone_tracking_service.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

@OutBoundPort
trait DroneRepository:
  def updatePosition(telemetry: DroneTelemetry): IO[Unit]
  def getPosition(request: TrackingRequest): IO[DroneTelemetry]
  
