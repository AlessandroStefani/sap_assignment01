package drone_api_gateway.infrastructure

import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import cats.effect.IO
import common.exagonal.Adapter
import drone_api_gateway.application.AccountService
import drone_api_gateway.domain.{Account, AccountPost}
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

@Adapter
class AccountServiceProxy(val client: Client[IO]) extends AccountService:

  override def registerUser(userName: String, password: String): IO[Account] =
    val targetUri = uri"prova"

    val req = Request[IO](Method.POST, targetUri).withEntity(AccountPost(userName, password))

    client.run(req).use:
      response =>
        val num = response.body.toString.toInt
        response.status match
          case Status.Ok => IO.pure(Account(num, userName, password))
          case status => IO.raiseError(new RuntimeException(s"Registration failed with status: $status"))

  override def loginUser(userName: String, password: String): IO[Boolean] =
    val targetUri = uri"http://localhost:8081/test/login"
    val req = Request[IO](Method.POST, targetUri).withEntity(AccountPost(userName, password))

    client.run(req).use { response =>
      response.status match {
        case Status.Ok => IO.pure(true)
        case _ => IO.pure(false)
      }
    }
