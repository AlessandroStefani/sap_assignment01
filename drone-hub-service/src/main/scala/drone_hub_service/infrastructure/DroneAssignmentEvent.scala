package drone_hub_service.infrastructure

import cats.effect.IO
import fs2.kafka.*
import io.circe.syntax.*
import io.circe.generic.auto.*

// evento che invieremo indietro all'order service
case class DroneAssignedEvent(orderId: String, droneId: String, userId: String)

object DroneAssignedPublisher:

  private val producerSettings = ProducerSettings[IO, String, String]
    .withBootstrapServers("kafka:9092")

  def publish(orderId: String, droneId: String, userId: String): IO[Unit] =
    val event = DroneAssignedEvent(orderId, droneId, userId).asJson.noSpaces
    val record = ProducerRecord("drone-assignments", orderId, event)

    KafkaProducer.stream(producerSettings)
      .evalMap { producer =>
        producer.produce(ProducerRecords.one(record))
      }
      .compile
      .drain