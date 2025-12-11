package drone_tracking_service.application

import cats.effect.IO

trait TrackingService:
  def trackDrone(droneId: String): IO[String] 
