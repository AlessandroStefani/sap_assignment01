package drone_hub_service.infrastructure

import cats.effect.IO
import fs2.kafka.*
import drone_hub_service.application.DroneHubService
import drone_hub_service.domain.{DroneOrderRequest, Order}
import io.circe.parser.decode
import io.circe.generic.auto.*

object OrderEventConsumer:

  def stream(droneHubService: DroneHubService): fs2.Stream[IO, Unit] =
    val consumerSettings = ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka:9092")
      .withGroupId("drone-hub-group")

    KafkaConsumer.stream(consumerSettings)
      .subscribeTo("orders")
      .records
      .evalMap { committable =>
        val process = for
          request <- IO.fromEither(decode[DroneOrderRequest](committable.record.value))
          droneId <- droneHubService.shipOrder(request.order)
          _       <- IO.println(s"[KafkaConsumer] Ordine ${request.order.id} processato. Drone assegnato: $droneId")

          _       <- DroneAssignedPublisher.publish(request.order.id.id, droneId.id, request.order.usrId)
          _       <- IO.println(s"[KafkaConsumer] Notifica assegnamento inviata su Kafka.")
        yield ()

        process.handleErrorWith(e => IO.println(s"Error processing order: ${e.getMessage}"))
          *> committable.offset.commit
      }