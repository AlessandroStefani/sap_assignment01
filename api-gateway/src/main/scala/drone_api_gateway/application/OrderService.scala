package drone_api_gateway.application

import cats.effect.IO
import drone_api_gateway.domain.Order

import java.time.Instant

trait OrderService:
  def placeOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant): IO[String]

  def getOrders(usrId: String): IO[List[Order]]