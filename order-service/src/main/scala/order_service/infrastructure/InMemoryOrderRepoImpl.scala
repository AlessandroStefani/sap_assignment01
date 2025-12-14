package order_service.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import order_service.application.OrderRepository
import order_service.domain.Order
import java.time.Instant
import scala.collection.concurrent.TrieMap

@Adapter
object InMemoryOrderRepoImpl extends OrderRepository:
  private val orders = TrieMap.empty[String, List[Order]]

  override def addOrder(usrId: String, order: Order): IO[Unit] = IO {
    val current = orders.getOrElse(usrId, List.empty)
    val updatedList = current.filterNot(_.id == order.id) :+ order
    orders.put(usrId, updatedList)
  }

  override def updateOrder(usrId: String, order: Order): IO[Unit] =
    addOrder(usrId, order)

  override def getUserOrders(usrId: String): IO[List[Order]] = IO {
    orders.getOrElse(usrId, List.empty)
  }

  override def getPendingOrders: IO[List[Order]] = IO {
    val now = Instant.now()
    orders.values.flatten
      .filter(o => o.droneId.isEmpty && !o.departureDate.isAfter(now))
      .toList
  }