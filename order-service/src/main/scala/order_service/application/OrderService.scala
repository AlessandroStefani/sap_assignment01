package order_service.application

import cats.effect.IO
import common.exagonal.InBoundPort
import order_service.domain.{Order, OrderId}

@InBoundPort
trait OrderService:
  def newOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: java.time.Instant): IO[OrderId]
  def getOrders(userId: String): IO[List[Order]]