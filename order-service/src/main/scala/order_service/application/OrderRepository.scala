package order_service.application

import cats.effect.IO
import common.ddd.Repository
import common.exagonal.OutBoundPort
import order_service.domain.Order

@OutBoundPort
trait OrderRepository extends Repository:
  def addOrder(usrId: String, order: Order): IO[Unit]
  def updateOrder(usrId: String, order: Order): IO[Unit]
  def getUserOrders(usrId: String): IO[List[Order]]
  def getPendingOrders: IO[List[Order]]