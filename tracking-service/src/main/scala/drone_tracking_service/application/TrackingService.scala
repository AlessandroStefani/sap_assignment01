package drone_tracking_service.application

import cats.effect.IO
import common.exagonal.InBoundPort
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

@InBoundPort
trait TrackingService:
  def trackDrone(request: TrackingRequest): IO[DroneTelemetry]