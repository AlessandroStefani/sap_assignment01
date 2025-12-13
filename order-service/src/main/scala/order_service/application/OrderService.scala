package order_service.application

import cats.effect.IO
import common.exagonal.InBoundPort
import order_service.domain.{DroneId, Order}

@InBoundPort
trait OrderService:
  def newOrder(userId: String, origin: String, destination: String, weight: Double): IO[DroneId]
  def getOrders(userId: String): IO[List[Order]]
