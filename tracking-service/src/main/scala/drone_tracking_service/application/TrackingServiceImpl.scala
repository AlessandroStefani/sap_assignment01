package drone_tracking_service.application

import cats.effect.{IO, Ref}
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

class TrackingServiceImpl(repo: DroneRepository) extends TrackingService with DroneStateUpdater:

  override def updateDronePosition(telemetry: DroneTelemetry): IO[Unit] =
    repo.updatePosition(telemetry)
    
  override def trackDrone(request: TrackingRequest): IO[DroneTelemetry] =
    repo.getPosition(request)

object TrackingServiceImpl:
  def create(repo: DroneRepository): IO[TrackingServiceImpl] =
    IO.pure(new TrackingServiceImpl(repo))