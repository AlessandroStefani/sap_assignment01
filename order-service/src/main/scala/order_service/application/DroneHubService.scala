package order_service.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import order_service.domain.{DroneId, Order}

@OutBoundPort
trait DroneHubService:
  def shipOrder(order: Order): IO[DroneId]
