package drone_hub_service.application

import common.exagonal.OutBoundPort
import drone_hub_service.domain.DroneId

@OutBoundPort
trait DroneTracking:
  def updateDrone(id: DroneId, xpos: Double, ypos: Double, tta: Int): Unit //modifica come vuoi
