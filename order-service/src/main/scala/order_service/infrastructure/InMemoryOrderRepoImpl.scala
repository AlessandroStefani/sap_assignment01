package order_service.infrastructure

import common.exagonal.Adapter
import order_service.application.OrderRepository
import order_service.domain.Order

import scala.collection.mutable

@Adapter
object InMemoryOrderRepoImpl extends OrderRepository:

  private val orders: mutable.Map[String, List[Order]] = mutable.Map.empty

  override def addOrder(usrId: String, order: Order): Unit =
    val currentOrders = orders.getOrElse(usrId, List.empty)
    orders.update(usrId, currentOrders :+ order)

  override def getUserOrders(usrId: String): List[Order] = orders.getOrElse(usrId, List.empty)
