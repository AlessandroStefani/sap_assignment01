package drone_hub_service.domain

case class TrackingUpdate(droneId: String, orderId: String, lat: Double, lon: Double, tta: Int)