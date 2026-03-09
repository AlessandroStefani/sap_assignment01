package order_service.domain

case class DroneAssignedEvent(orderId: String, droneId: String, userId: String)
