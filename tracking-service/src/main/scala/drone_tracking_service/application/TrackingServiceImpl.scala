package drone_tracking_service.application

class TrackingServiceImpl extends TrackingService:
  override def trackDrone(droneId: String): cats.effect.IO[String] =
    cats.effect.IO.pure(s"Tracking information for drone $droneId")
