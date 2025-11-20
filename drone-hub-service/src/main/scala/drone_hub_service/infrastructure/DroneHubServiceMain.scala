package drone_hub_service.infrastructure

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import com.comcast.ip4s.host
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object DroneHubServiceMain extends IOApp {
  private val DRONEHUB_PORT = port"9000"

  private def routes(client: Client[IO]): HttpRoutes[IO] = ???
  
  override def run(args: List[String]): IO[ExitCode] =
    val appResource = for
      client <- EmberClientBuilder.default[IO].build
      httpApp = Logger.httpApp(true, true)(routes(client).orNotFound)

      server <- EmberServerBuilder
        .default[IO]
        .withHost(host"localhost")
        .withPort(DRONEHUB_PORT)
        .withHttpApp(httpApp)
        .build
    yield server


    appResource
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
