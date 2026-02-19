package drone_hub_service.domain.agent

import cats.effect.{Deferred, IO}
import drone_hub_service.domain.Order


/** Definisce l'insieme delle regole di azione (Think -> Act) che l'agente può applicare. */
enum DroneActionRules:
  case RechargeBattery
  case EvaluateOrder(order: Order, replyTo: Deferred[IO, Boolean])
  case AdjustRotorsAndFly(mode: String, powerLevel: Int, drain: Double)
  case NextFlightPhase(phaseName: String, ticks: Int)
  case EndFlight