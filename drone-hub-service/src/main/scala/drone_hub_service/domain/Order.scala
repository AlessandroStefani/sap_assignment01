package drone_hub_service.domain

import common.ddd.Entity

import java.time.Instant

case class Order(
                  id: OrderId,
                  usrId: String,
                  weight: Double,
                  origin: String,
                  destination: String,
                  departureDate: Instant,
                  droneId: Option[DroneId]
                ) extends Entity[OrderId]:
  override def getId: OrderId = id
