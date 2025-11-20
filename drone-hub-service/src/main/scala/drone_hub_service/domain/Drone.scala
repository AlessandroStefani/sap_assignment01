package drone_hub_service.domain

import common.ddd.Entity

case class Drone(id: DroneId) extends Entity[DroneId] {

  override def getId: DroneId = id
}
