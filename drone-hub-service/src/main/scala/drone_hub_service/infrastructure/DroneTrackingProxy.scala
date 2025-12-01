package drone_hub_service.infrastructure

import common.exagonal.Adapter
import drone_hub_service.application.DroneTracking
import drone_hub_service.domain.DroneId

@Adapter
class DroneTrackingProxy extends DroneTracking:

  override def updateDrone(id: DroneId, lat: Double, lon: Double, tta: Int): Unit = ???
