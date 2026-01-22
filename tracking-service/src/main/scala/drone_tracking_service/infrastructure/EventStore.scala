package drone_tracking_service.infrastructure

import cats.effect.{IO, Ref, Resource}
import drone_tracking_service.domain.DroneTelemetry
import fs2.io.file.{Files, Path}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.time.Instant

trait EventStore:
  def persist(event: DroneTelemetry): IO[Unit]

class FileEventStore private (filePath: Path) extends EventStore:

  override def persist(event: DroneTelemetry): IO[Unit] =
    val jsonLine = event.asJson.noSpaces + Instant.now().toString + "\n"
    fs2.Stream(jsonLine)
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(filePath, fs2.io.file.Flags.Append))
      .compile
      .drain

object FileEventStore:

  def make(filePathString: String): Resource[IO, (EventStore, Ref[IO, Map[String, DroneTelemetry]])] =
    val path = Path(filePathString)

    Resource.eval(for
      _ <- initializeFile(path)
      history <- replay(path)
      initialMap = history.foldLeft(Map.empty[String, DroneTelemetry]) { (acc, telemetry) =>
        acc + (telemetry.droneId -> telemetry)
      }
      stateRef <- Ref.of[IO, Map[String, DroneTelemetry]](initialMap)
      store = new FileEventStore(path)
    yield (store, stateRef))

  private def initializeFile(path: Path): IO[Unit] =
    Files[IO].exists(path).flatMap { exists =>
      if !exists then
        IO.println(s"Creating new event log file: $path") *>
          Files[IO].createDirectories(path.parent.get) *>
          fs2.Stream.empty.through(Files[IO].writeAll(path)).compile.drain
      else
        IO.println(s"Found existing event log file: $path")
    }

  private def replay(path: Path): IO[List[DroneTelemetry]] =
    Files[IO].readAll(path)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .map(decode[DroneTelemetry](_).toOption)
      .collect { case Some(t) => t }
      .compile
      .toList