package drone_api_gateway.infrastructure

import cats.effect.IO
import common.exagonal.Adapter
import drone_api_gateway.application.AccountService
import drone_api_gateway.domain.Account
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.implicits.uri

@Adapter
class AccountServiceProxy(val client: Client[IO]) extends AccountService:

  override def registerUser(userName: String, password: String): IO[Account] = ???

  override def loginUser(userName: String, password: String): IO[Boolean] =
    val targetUri = uri"http://localhost:8081/test/login"
    val req = org.http4s.Request[IO](org.http4s.Method.POST, targetUri)

    client.run(req).use { response =>
      response.status match {
        case Status.Ok => IO.pure(true)
        case _ => IO.pure(false)
      }
    }
