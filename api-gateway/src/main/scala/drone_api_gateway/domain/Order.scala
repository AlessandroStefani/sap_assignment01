package drone_api_gateway.domain

import java.time.Instant

case class OrderId(id: String)
case class DroneId(id: String)

case class Order(
                  id: OrderId,
                  usrId: String,
                  weight: Double,
                  origin: String,
                  destination: String,
                  departureDate: Instant,
                  droneId: Option[DroneId]
                )