package order_service.domain

import java.time.Instant

case class NewOrderRequest(userId: String, origin: String, destination: String, weight: Double, departureDate: Instant)
