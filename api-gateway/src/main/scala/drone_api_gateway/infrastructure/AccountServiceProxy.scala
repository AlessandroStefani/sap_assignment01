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

  //docker routes
  val dockerUriRegister = uri"http://account-service:8081/test/register"
  val dockerUriLogin = uri"http://account-service:8081/test/login"

  override def registerUser(userName: String, password: String): IO[Account] =
    val targetUri = uri"http://localhost:8081/test/register"
    // todo() cambiare con le rotte sopra per docker!!!!!!!!!
    val req = Request[IO](Method.POST, targetUri).withEntity(AccountPost(userName, password))

    client.run(req).use:
      response =>
        response.status match
          case Status.Ok => 
          //IO.pure(Account(1, userName, password))
            response.as[Account]
          case status => IO.raiseError(new RuntimeException(s"Registration failed with status: $status"))

  override def loginUser(userName: String, password: String): IO[Boolean] =
    val targetUri = uri"http://localhost:8081/test/login"
    // todo() cambiare con le rotte sopra per docker!!!!!!!!!
    val req = Request[IO](Method.POST, targetUri).withEntity(AccountPost(userName, password))

    client.run(req).use { response =>
      response.status match {
        case Status.Ok => IO.pure(true)
        case Status.Found => throw Exception("gia loggato")
        case _ => IO.pure(false)
      }
    }

  override def logoutUser(userName: String): IO[Boolean] =
    val targetUri = uri"http://localhost:8081/test/logout"
    val req = Request[IO](Method.POST, targetUri).withEntity(AccountPost(userName, ""))

    client.run(req).use: response =>
      response.status match
        case Status.Ok => IO.pure(true)
        case Status.NotFound => IO.pure(false)
        case status => IO.raiseError(new RuntimeException(s"Logout failed with status: $status"))
