package application

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import order_service.application.{OrderRepository, OrderServiceImpl}
import order_service.domain.{Order, OrderId}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class OrderServiceImplTest extends AnyFlatSpec with Matchers:
  
  class StubOrderRepository extends OrderRepository:
    var store: Map[String, List[Order]] = Map.empty

    override def addOrder(userId: String, order: Order): IO[Unit] = IO {
      val currentOrders = store.getOrElse(userId, List.empty)
      store = store + (userId -> (currentOrders :+ order))
    }

    override def getUserOrders(userId: String): IO[List[Order]] = IO {
      store.getOrElse(userId, List.empty)
    }

    override def updateOrder(usrId: String, order: Order): IO[Unit] = ???

    override def getPendingOrders: IO[List[Order]] = ???

  "OrderServiceImpl" should "create a new order and persist it correctly" in:
    val stubRepo = new StubOrderRepository()
    val service = new OrderServiceImpl(stubRepo)

    val userId = "test-user-01"
    val origin = "Cesena"
    val dest = "Bologna"
    val weight = 5.5
    val departure = Instant.now()

    val createdOrderId = service.newOrder(userId, origin, dest, weight, departure).unsafeRunSync()

    createdOrderId shouldBe a [OrderId]

    val savedOrders = stubRepo.getUserOrders(userId).unsafeRunSync()
    savedOrders should have size 1

    val savedOrder = savedOrders.head
    savedOrder.id shouldBe createdOrderId
    savedOrder.origin shouldBe origin
    savedOrder.weight shouldBe weight

  it should "retrieve existing orders for a user" in:
    val stubRepo = new StubOrderRepository()
    val service = new OrderServiceImpl(stubRepo)
    val userId = "test-user-02"

    val existingOrder = Order(
      id = OrderId("order-123"),
      usrId = userId,
      weight = 2.0,
      origin = "A",
      destination = "B",
      departureDate = Instant.now(),
      droneId = None
    )
    stubRepo.addOrder(userId, existingOrder).unsafeRunSync()

    val orders = service.getOrders(userId).unsafeRunSync()

    orders should have size 1
    orders.head.usrId shouldBe userId
    orders.head.id shouldBe existingOrder.id