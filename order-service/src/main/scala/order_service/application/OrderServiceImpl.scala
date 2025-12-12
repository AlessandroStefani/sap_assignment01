package order_service.application

import cats.effect.IO
import order_service.domain.DroneId

class OrderServiceImpl extends OrderService:
  override def newOrder(origin: String, destination: String, weight: Double): IO[DroneId] = ???
