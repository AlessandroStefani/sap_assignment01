package order_service.infrastructure

import common.exagonal.Adapter
import order_service.application.OrderRepository
import order_service.domain.Order

import scala.collection.concurrent.TrieMap

@Adapter
object InMemoryOrderRepoImpl extends OrderRepository:
  private val orders = TrieMap.empty[String, List[Order]]

  override def addOrder(usrId: String, order: Order): Unit =
    val current = orders.getOrElse(usrId, List.empty)
    orders.put(usrId, current :+ order)

  override def getUserOrders(usrId: String): List[Order] =
    orders.getOrElse(usrId, List.empty)