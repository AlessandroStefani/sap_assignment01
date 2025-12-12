package drone_hub_service.application

import drone_hub_service.domain.{Drone, DroneId, Order}

class DroneHubServiceImpl extends DroneHubService:

  private val fleetSize = 10
  private val fleet: List[Drone] = (1 to fleetSize).map(n => Drone(DroneId(n.toString))).toList

  override def shipOrder(order: Order): DroneId =
    fleet.find(_.isAvailable) match
      case Some(drone) =>
        drone.deliver(order)

        println(s"[DroneHub] Order assigned to drone: ${drone.getId}")
        drone.getId

      case None =>
        throw new RuntimeException("All drones are currently busy. Please try again later.")
  