package drone_hub_service.domain.agent

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Ref}
import common.ddd.Entity
import drone_hub_service.application.DroneStateUpdater
import drone_hub_service.domain.*
import drone_hub_service.domain.agent.DronePercept.*
import drone_hub_service.domain.agent.DroneActionRules.*
import drone_hub_service.domain.agent.environment.Wind

import scala.concurrent.duration.*
import scala.util.Random

/** L'Agente Drone Intelligente.
 * Implementa un'architettura reattiva pura con ciclo Percept -> Rule Match -> Apply.
 * Comunica in modalità message-passing tramite coda asincrona (Contract Net protocol). */
class Drone private (
                      id: DroneId,
                      tracker: DroneStateUpdater,
                      inbox: Queue[IO, (Order, Deferred[IO, Boolean])],
                      currentStateRef: Ref[IO, DroneState]
                    ) extends Entity[DroneId]:

  override def getId: DroneId = id

  /** L'Hub invia una proposta di consegna all'agente.
   * L'agente valuterà la proposta nel suo ciclo principale e risponderà asincronamente. */
  def proposeDelivery(order: Order): IO[Boolean] =
    currentStateRef.get.flatMap:
      case DroneState.InBase_Standby =>
        for
          replyDeferred <- Deferred[IO, Boolean]
          _             <- inbox.offer((order, replyDeferred))
          result        <- replyDeferred.get
        yield result
      case _ =>
        IO.pure(false)

  /** Il loop principale dell'agente, infinito e ricorsivo di percezione-decisione-azione. */
  def agentLoop(mem: AgentMemory): IO[Unit] =
    for
      _ <- IO.sleep(1.second)

      percept <- mem.state match
        case DroneState.InBase_Standby =>
          inbox.tryTake.map:
            case Some((order, replyTo)) => OrderReceived(order, replyTo)
            case None                   => BaseIdle

        case DroneState.InBase_ValutazioneOrdine =>
          IO.pure(BaseIdle)

        case DroneState.InVolo =>
          if mem.ticksInPhase <= 0 then IO.pure(FlightPhaseComplete)
          else
            val wind = Random.nextInt(10) match
              case x if x <= 3 => Wind.Headwind
              case x if x >= 7 => Wind.Tailwind
              case _           => Wind.NoWind
            IO.pure(WindObserved(wind))

      rule = (mem.state, percept) match
        case (DroneState.InBase_Standby, BaseIdle)            => RechargeBattery
        case (DroneState.InBase_Standby, OrderReceived(o, r)) => EvaluateOrder(o, r)

        case (DroneState.InVolo, WindObserved(Wind.Headwind)) => AdjustRotorsAndFly("HighPower", 100, 2.0)
        case (DroneState.InVolo, WindObserved(Wind.Tailwind)) => AdjustRotorsAndFly("LowPower", 30, 0.5)
        case (DroneState.InVolo, WindObserved(Wind.NoWind))   => AdjustRotorsAndFly("NormalPower", 60, 1.0)

        case (DroneState.InVolo, FlightPhaseComplete) =>
          mem.flightPhase match
            case "ToOrigin" => NextFlightPhase("Flying", mem.totalFlightDuration - 5)
            case "Flying"   => NextFlightPhase("ToBase", 5)
            case "ToBase"   => EndFlight

        case _ => RechargeBattery

      nextMem <- applyRule(rule, mem)

      _ <- currentStateRef.set(nextMem.state)

      _ <- agentLoop(nextMem)
    yield ()

  /** Esegue l'azione decisa e restituisce la memoria interna aggiornata. */
  private def applyRule(rule: DroneActionRules, mem: AgentMemory): IO[AgentMemory] = rule match
    case RechargeBattery =>
      val nextBatt = (mem.battery + 5.0).min(100.0)
      IO.pure(mem.copy(battery = nextBatt))

    case EvaluateOrder(order, replyTo) =>
      for
        _ <- IO.println(s"🤔 [DRONE ${id.id}] Stato: Standby -> InBase_ValutazioneOrdine")
        flightSecs = Random.nextInt(11) + 10
        totalTicks = 5 + flightSecs + 5
        worstCaseDrain = totalTicks * 2.0

        nextMem <- if mem.battery >= worstCaseDrain then
          IO.println(s"✅ [DRONE ${id.id}] Batt OK (${mem.battery.toInt}% > stima ${worstCaseDrain.toInt}%). Accetto -> InVolo") *>
            replyTo.complete(true) *>
            IO.pure(mem.copy(
              state = DroneState.InVolo,
              currentOrder = Some(order),
              flightPhase = "ToOrigin",
              ticksInPhase = 5,
              totalFlightDuration = totalTicks,
              lat = 44.0,
              lon = 12.0
            ))
        else
          IO.println(s"❌ [DRONE ${id.id}] Batt INSUFFICIENTE (${mem.battery.toInt}% < stima ${worstCaseDrain.toInt}%). Rifiuto -> Standby") *>
            replyTo.complete(false) *>
            IO.pure(mem.copy(state = DroneState.InBase_Standby))
      yield nextMem

    case AdjustRotorsAndFly(mode, powerLevel, drain) =>
      val newBatt = mem.battery - drain
      val newLat = mem.lat + 0.1
      val newLon = mem.lon + 0.1
      for
        _ <- IO.println(s"🛸 [DRONE ${id.id}] [${mem.flightPhase}] Vento: ${mode.replace("Power","")} -> Rotori al $powerLevel% | Batt: ${newBatt.toInt}%")
        _ <- tracker.updateDrone(id, mem.currentOrder.get, newLat, newLon, mem.totalFlightDuration - 1)
      yield mem.copy(battery = newBatt, rotorPower = powerLevel, lat = newLat, lon = newLon, ticksInPhase = mem.ticksInPhase - 1, totalFlightDuration = mem.totalFlightDuration - 1)

    case NextFlightPhase(nextPhase, ticks) =>
      val msg = if nextPhase == "Flying" then "Arrivato all'origine. Prelevo pacco e vado a destinazione..."
      else "Consegnato a destinazione! Ritorno in base..."
      IO.println(s"📦 [DRONE ${id.id}] $msg") *>
        IO.pure(mem.copy(flightPhase = nextPhase, ticksInPhase = ticks))

    case EndFlight =>
      IO.println(s"🏠 [DRONE ${id.id}] Rientrato in base. Stato: InVolo -> InBase_Standby") *>
        IO.pure(mem.copy(state = DroneState.InBase_Standby, currentOrder = None, rotorPower = 0))


object Drone:
  /** Crea un Drone, la sua Inbox per la comunicazione asincrona e avvia il suo ciclo di vita in background. */
  def create(id: DroneId, tracker: DroneStateUpdater): IO[Drone] =
    for
      inbox <- Queue.unbounded[IO, (Order, Deferred[IO, Boolean])]
      stateRef <- Ref.of[IO, DroneState](DroneState.InBase_Standby)
      drone = new Drone(id, tracker, inbox, stateRef)

      initialMemory = AgentMemory(
        state = DroneState.InBase_Standby,
        battery = 100.0,
        rotorPower = 0,
        currentOrder = None,
        flightPhase = "",
        ticksInPhase = 0,
        totalFlightDuration = 0,
        lat = 44.0,
        lon = 12.0
      )

      _ <- drone.agentLoop(initialMemory).start
    yield drone