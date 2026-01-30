package order_service.infrastructure

import cats.effect.*
import cats.effect.std.Semaphore
import cats.syntax.all.*
import common.exagonal.Adapter
import fs2.io.file.{Files, Path}
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import order_service.application.OrderRepository
import order_service.domain.Order

import java.time.Instant

@Adapter
class FileOrderRepository private (
                                    state: Ref[IO, Map[String, List[Order]]],
                                    path: Path,
                                    mutex: Semaphore[IO]
                                  ) extends OrderRepository:
  
  private def persist(currentState: Map[String, List[Order]]): IO[Unit] =
    val jsonString = currentState.asJson.spaces2
    fs2.Stream(jsonString)
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(path))
      .compile
      .drain

  override def addOrder(usrId: String, order: Order): IO[Unit] =
    mutex.permit.use { _ =>
      for
        _ <- state.update { current =>
          val userOrders = current.getOrElse(usrId, List.empty)
          val updatedList = userOrders.filterNot(_.id == order.id) :+ order
          current.updated(usrId, updatedList)
        }
        newState <- state.get
        _ <- persist(newState)
      yield ()
    }

  override def updateOrder(usrId: String, order: Order): IO[Unit] =
    addOrder(usrId, order)

  override def getUserOrders(usrId: String): IO[List[Order]] =
    state.get.map(_.getOrElse(usrId, List.empty))

  override def getPendingOrders: IO[List[Order]] =
    for
      currentState <- state.get
      now <- IO(Instant.now())
    yield currentState.values.flatten
      .filter(o => o.droneId.isEmpty && !o.departureDate.isAfter(now))
      .toList

object FileOrderRepository:
  def make(filePath: String): Resource[IO, OrderRepository] =
    val path = Path(filePath)

    Resource.eval(for
      mutex <- Semaphore[IO](1)
      exists <- Files[IO].exists(path)

      initialState <- if exists then
        Files[IO].readAll(path)
          .through(fs2.text.utf8.decode)
          .compile.string
          .flatMap { str =>
            IO.fromEither(decode[Map[String, List[Order]]](str))
              .handleErrorWith(_ => IO.println("File JSON corrotto o vuoto, parto da zero") *> IO.pure(Map.empty))
          }
      else
        IO.pure(Map.empty[String, List[Order]])

      ref <- Ref.of[IO, Map[String, List[Order]]](initialState)
      _ <- IO.println(s"FileOrderRepository inizializzato su: $filePath")
    yield new FileOrderRepository(ref, path, mutex))