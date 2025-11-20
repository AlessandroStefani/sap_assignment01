package drone_hub_service.application

import common.exagonal.InBoundPort
import drone_hub_service.domain.DroneId

@InBoundPort
trait DroneHubService:
  def newOrder(origin: String, destination: String, weight: Double): DroneId
