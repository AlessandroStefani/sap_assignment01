package drone_tracking_service.domain

case class DroneTelemetry(droneId: String, orderId: String, lat: Double, lon: Double, tta: Int)