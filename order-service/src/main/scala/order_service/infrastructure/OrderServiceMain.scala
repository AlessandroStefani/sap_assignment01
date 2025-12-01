package order_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

class OrderServiceMain extends IOApp:
  private val ORDER_SERVICE_PORT = port"9068"

  private def routes(service: ???): HttpRoutes[IO] = HttpRoutes.of[IO]:
  case req@POST -> Root / "order" / "new" => ???

  override def run(args: List[String]): IO[ExitCode] =
  val httpApp = Logger.httpApp(true, true)(routes(???).orNotFound)

  IO.println(s"Order Service is starting on port $ORDER_SERVICE_PORT...") *>
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(ORDER_SERVICE_PORT)
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)

