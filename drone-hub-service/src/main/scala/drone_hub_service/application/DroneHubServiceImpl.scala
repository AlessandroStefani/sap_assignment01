package drone_hub_service.application

import cats.effect.IO
import cats.implicits.*
import drone_hub_service.domain.agent.Drone
import drone_hub_service.domain.{DroneId, Order}

class DroneHubServiceImpl private (
                                    fleet: List[Drone],
                                    trackingService: DroneStateUpdater
                                  ) extends DroneHubService:

  override def shipOrder(order: Order): IO[DroneId] =
    def tryAssign(drones: List[Drone]): IO[DroneId] = drones match {
      case Nil =>
        IO.raiseError(new RuntimeException("Tutti i droni sono occupati o hanno rifiutato l'ordine."))
      case drone :: tail =>
        drone.proposeDelivery(order).flatMap { accepted =>
          if (accepted) IO.pure(drone.getId)
          else tryAssign(tail)
        }
    }
    tryAssign(fleet)

object DroneHubServiceImpl:
  def create(trackingService: DroneStateUpdater, fleetSize: Int = 10): IO[DroneHubService] =
    val ioDrones: List[IO[Drone]] = (1 to fleetSize).toList.map { n =>
      Drone.create(DroneId(n.toString), trackingService)
    }

    for
      fleet <- ioDrones.sequence
      _     <- IO.println(s"[HUB] Flotta di $fleetSize droni istanziata e in ricarica")
    yield new DroneHubServiceImpl(fleet, trackingService)