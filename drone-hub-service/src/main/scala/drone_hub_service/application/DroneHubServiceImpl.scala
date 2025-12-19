package drone_hub_service.application

import cats.effect.IO
import drone_hub_service.domain.{Drone, DroneId, Order}

class DroneHubServiceImpl(trackingService: DroneStateUpdater) extends DroneHubService:

  private val fleetSize = 10
  private val fleet: List[Drone] = (1 to fleetSize).map { n =>
    Drone(DroneId(n.toString), trackingService)
  }.toList

  override def shipOrder(order: Order): IO[DroneId] =
    fleet.find(_.isAvailable) match
      case Some(drone) =>
        for
          _ <- drone.deliver(order)
          _ <- IO.println(s"[DroneHub] Order assigned to drone: ${drone.getId.id}")
        yield drone.getId

      case None =>
        IO.raiseError(new RuntimeException("All drones are currently busy. Please try again later."))