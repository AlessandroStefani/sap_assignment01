package drone_hub_service.application

import common.exagonal.InBoundPort
import drone_hub_service.domain.{DroneId, Order}

@InBoundPort
trait DroneHubService:
  def shipOrder(order: Order): DroneId
