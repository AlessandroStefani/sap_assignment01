package drone_api_gateway.application

import cats.effect.IO
import drone_api_gateway.domain.tracking.{TrackingRequest, DroneTelemetry}

trait TrackingService:
  def trackDrone(trackingRequest: TrackingRequest): IO[DroneTelemetry]
