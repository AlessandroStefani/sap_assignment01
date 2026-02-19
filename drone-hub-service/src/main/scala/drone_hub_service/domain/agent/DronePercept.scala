package drone_hub_service.domain.agent

import cats.effect.{Deferred, IO}
import drone_hub_service.domain.Order
import drone_hub_service.domain.agent.environment.Wind


/** Definisce l'insieme delle percezioni (Sense) che l'agente può rilevare dall'ambiente. */
enum DronePercept:
  case OrderReceived(order: Order, replyTo: Deferred[IO, Boolean])
  case WindObserved(wind: Wind)
  case BaseIdle
  case FlightPhaseComplete