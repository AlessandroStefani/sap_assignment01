package drone_tracking_service.application

import cats.effect.{IO, Ref}
import drone_tracking_service.domain.{DroneTelemetry, TrackingRequest}

class TrackingServiceImpl(state: Ref[IO, Map[String, DroneTelemetry]]) extends TrackingService:

  def updateDronePosition(telemetry: DroneTelemetry): IO[Unit] =
    state.update { currentMap =>
      // Aggiorna o inserisce la telemetria per quel droneId
      currentMap + (telemetry.droneId -> telemetry)
    } *> IO.println(s"[TrackingService] Updated telemetry for drone ${telemetry.droneId} carrying order ${telemetry.orderId}")

  override def trackDrone(request: TrackingRequest): IO[DroneTelemetry] =
    state.get.flatMap { currentMap =>
      currentMap.get(request.droneId) match
        case Some(telemetry) =>
          // Controllo incrociato: verifica che il drone stia portando proprio quell'ordine
          if (telemetry.orderId == request.orderId) then
            IO.pure(telemetry)
          else
            IO.raiseError(new IllegalArgumentException(s"Mismatch: Drone ${request.droneId} is not carrying order ${request.orderId}"))
        case None =>
          IO.raiseError(new IllegalArgumentException(s"No telemetry found for drone ${request.droneId}"))
    }

object TrackingServiceImpl:
  // Factory method per creare il servizio inizializzando la Ref vuota
  def create: IO[TrackingServiceImpl] =
    Ref.of[IO, Map[String, DroneTelemetry]](Map.empty).map(new TrackingServiceImpl(_))