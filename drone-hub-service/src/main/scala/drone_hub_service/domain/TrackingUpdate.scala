package drone_hub_service.domain

case class TrackingUpdate(droneId: DroneId, orderId: String, lat: Double, lon: Double, tta: Int)