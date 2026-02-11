package order_service.infrastructure

import cats.effect.IO
import fs2.kafka.*
import order_service.application.DroneHubService
import order_service.domain.{DroneId, DroneOrderRequest, Order}
import io.circe.syntax.*
import io.circe.generic.auto.*

class OrderEventPublisher extends DroneHubService:

  private val producerSettings = ProducerSettings[IO, String, String]
    .withBootstrapServers("kafka:9092")

  override def shipOrder(order: Order): IO[DroneId] =
    val payload = DroneOrderRequest(order).asJson.noSpaces
    val record = ProducerRecord("orders", order.id.id, payload)

    KafkaProducer.stream(producerSettings)
      .evalMap { producer =>
        producer.produce(ProducerRecords.one(record))
      }
      .compile
      .drain
      .as(DroneId("pending"))