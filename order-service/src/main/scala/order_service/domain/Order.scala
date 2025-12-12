package order_service.domain

import common.ddd.Entity

case class Order(id: OrderId, usrId: String, weight: Double) extends Entity[OrderId]
