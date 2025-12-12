package drone_hub_service.application

import cats.effect.IO
import common.exagonal.OutBoundPort
import drone_hub_service.domain.Order
import drone_hub_service.domain.DroneId

@OutBoundPort
trait DroneTracking:
  def updateDrone(id: DroneId, order: Order, xpos: Double, ypos: Double, tta: Int): IO[Unit] //modifica come vuoi
