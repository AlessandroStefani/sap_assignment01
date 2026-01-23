package drone_tracking_service.infrastructure

import cats.effect.{IO, Ref}
import common.exagonal.Adapter
import drone_tracking_service.application.DroneRepository
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

@Adapter
class InMemoryRepository(state: Ref[IO, Map[String, DroneTelemetry]]) extends DroneRepository:
  override def updatePosition(telemetry: DroneTelemetry): IO[Unit] =
    state.update { currentMap =>
      currentMap + (telemetry.droneId -> telemetry)
    } *> IO.println(s"[TrackingService] Updated telemetry for drone ${telemetry.droneId} carrying order ${telemetry.orderId}")


  override def getPosition(request: TrackingRequest): IO[DroneTelemetry] =
    state.get.flatMap { currentMap =>
      currentMap.get(request.droneId) match
        case Some(telemetry) =>
          if (telemetry.orderId == request.orderId) then
            IO.pure(telemetry)
          else
            IO.raiseError(new IllegalArgumentException(s"Mismatch: Drone ${request.droneId} is not carrying order ${request.orderId}"))
        case None =>
          IO.raiseError(new IllegalArgumentException(s"No telemetry found for drone ${request.droneId}"))
    }
    
object InMemoryRepository:
  def create: IO[InMemoryRepository] =
    Ref.of[IO, Map[String, DroneTelemetry]](Map.empty).map(new InMemoryRepository(_))
