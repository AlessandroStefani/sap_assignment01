package drone_hub_service.domain.agent

import drone_hub_service.domain.Order

/** Rappresenta la memoria (stato interno immutabile) dell'agente.
 * Viene passata e aggiornata a ogni ciclo del loop di "vita" dell'agente. */
case class AgentMemory(
                        state: DroneState,
                        battery: Double,
                        rotorPower: Int,
                        currentOrder: Option[Order],
                        flightPhase: String,
                        ticksInPhase: Int,
                        flightDurationSecs: Int,
                        lat: Double,
                        lon: Double
                      )
