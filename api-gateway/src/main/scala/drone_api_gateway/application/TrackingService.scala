package drone_api_gateway.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import drone_api_gateway.domain.tracking.{DroneTelemetry, TrackingRequest}

@OutBoundPort
trait TrackingService:
  def trackDrone(trackingRequest: TrackingRequest): IO[DroneTelemetry]
