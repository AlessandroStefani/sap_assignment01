package drone_tracking_service.application

import cats.effect.{IO, Ref}
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}
import drone_tracking_service.infrastructure.EventStore

class TrackingServiceImpl(state: Ref[IO, Map[String, DroneTelemetry]], eventStore: EventStore) extends TrackingService with DroneStateUpdater:

  override def updateDronePosition(telemetry: DroneTelemetry): IO[Unit] =
    eventStore.persist(telemetry) *>
    state.update { currentMap =>
      currentMap + (telemetry.droneId -> telemetry)
    } *> IO.println(s"[TrackingService] Updated telemetry for drone ${telemetry.droneId} carrying order ${telemetry.orderId}")

  override def trackDrone(request: TrackingRequest): IO[DroneTelemetry] =
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
