package drone_hub_service.domain

case class TrackingUpdate(droneId: DroneId, lat: Double, lon: Double, tta: Int)
