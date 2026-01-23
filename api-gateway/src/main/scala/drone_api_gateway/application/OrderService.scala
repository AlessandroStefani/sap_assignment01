package drone_api_gateway.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import drone_api_gateway.domain.Order

import java.time.Instant

@OutBoundPort
trait OrderService:
  def placeOrder(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant): IO[String]

  def getOrders(usrId: String): IO[List[Order]]