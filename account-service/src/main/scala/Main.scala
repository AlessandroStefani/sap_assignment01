import cats.effect.*
import com.comcast.ip4s.*
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.circe.CirceSensitiveDataEntityDecoder.circeEntityDecoder
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger


case class Account(id: Int, username: String, password: String)

case class AccountPost(name: String, password: String)

object Main extends IOApp:

  private var x = 0

  private val helloWorldService: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "test" / "login" =>
      Ok("ok")

    case req @ POST -> Root / "test" / "register" =>
      req.as[AccountPost].flatMap { inputData =>
        x = x + 1
        val newAccount = Account(x, inputData.name, inputData.password)
        Ok(newAccount)
      }

    case _ => ServiceUnavailable("Rotta non trovata")
  }

  private val httpApp = Logger.httpApp(true, true)(helloWorldService.orNotFound)

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"localhost")
      .withPort(port"8081")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)