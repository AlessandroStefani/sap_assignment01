package drone_tracking_service.application

import cats.effect.IO
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}
import common.exagonal.InBoundPort

@InBoundPort
trait TrackingService:
  def trackDrone(request: TrackingRequest): IO[DroneTelemetry]