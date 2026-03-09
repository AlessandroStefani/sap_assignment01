package drone_hub_service.domain

case class DroneAssignedEvent(orderId: String, droneId: String, userId: String)
