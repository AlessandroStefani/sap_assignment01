package order_service.infrastructure

import cats.effect.IO
import fs2.kafka.*
import order_service.application.OrderRepository
import order_service.domain.DroneId
import io.circe.parser.decode
import io.circe.generic.auto.*
import order_service.domain.DroneAssignedEvent

object DroneIdConsumer:

  def stream(orderRepo: OrderRepository): fs2.Stream[IO, Unit] =
    val consumerSettings = ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka:9092")
      .withGroupId("order-service-group")

    KafkaConsumer.stream(consumerSettings)
      .subscribeTo("drone-assignments")
      .records
      .evalMap { committable =>
        val process = for
          event  <- IO.fromEither(decode[DroneAssignedEvent](committable.record.value))
          _      <- IO.println(s"[KafkaConsumer] Ricevuto drone ${event.droneId} per l'ordine ${event.orderId}")

          // Recuperiamo gli ordini dell'utente per trovare quello specifico
          orders <- orderRepo.getUserOrders(event.userId)
          orderOpt = orders.find(_.id.id == event.orderId)

          _ <- orderOpt match
            case Some(order) =>
              // Aggiorniamo l'ordine con il drone id ricevuto
              val updatedOrder = order.copy(droneId = Some(DroneId(event.droneId)))
              orderRepo.updateOrder(event.userId, updatedOrder) *>
                IO.println(s"[KafkaConsumer] Ordine ${event.orderId} aggiornato con successo nel database.")
            case None =>
              IO.println(s"[KafkaConsumer-WARNING] Ordine ${event.orderId} non trovato per l'utente ${event.userId}.")
        yield ()

        process.handleErrorWith(e => IO.println(s"Error processing drone assignment: ${e.getMessage}"))
          *> committable.offset.commit
      }