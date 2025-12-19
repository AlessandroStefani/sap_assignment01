package drone_api_gateway.domain.tracking

case class DroneTelemetry(droneId: String, orderId: String, lat: Double, lon: Double, tta: Int)