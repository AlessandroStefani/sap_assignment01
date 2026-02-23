package e2e

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.literal.*
import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, ScalaDsl}
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.JavaNetClientBuilder
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.jdk.CollectionConverters.*

class SystemUserJourneySteps extends ScalaDsl with EN with Matchers {

  private val gatewayBaseUrl = sys.env.getOrElse("API_GATEWAY_URL", "http://localhost:8080")
  private val apiRoot = Uri.unsafeFromString(gatewayBaseUrl) / "test"
  private val COOKIE_NAME = "drone_auth_session"

  private val client = JavaNetClientBuilder[IO].create

  private var username = ""
  private val password = "password123"
  private var authCookie: Option[RequestCookie] = None
  private var ordersList: Vector[Json] = Vector.empty
  private var lastStatus: Status = Status.Ok

  private def credentialsJson = json"""{
    "username": $username,
    "password": $password
  }"""

  Given("the system starts from a clean state") { () =>
    val logoutReq = Request[IO](Method.POST, apiRoot / "logout")
    client.run(logoutReq).use_.unsafeRunSync()

    username = s"user_${System.currentTimeMillis()}"
  }

  When("a new user registers with valid username and password") { () =>
    val registerReq = Request[IO](Method.POST, apiRoot / "register").withEntity(credentialsJson)
    val regStatus = client.status(registerReq).unsafeRunSync()

    withClue(s"Registration failed with status $regStatus") {
      regStatus should (be(Status.Created) or be(Status.Conflict))
    }
  }

  When("the user logs in with the same credentials") { () =>
    val loginReq = Request[IO](Method.POST, apiRoot / "login").withEntity(credentialsJson)

    authCookie = client.run(loginReq).use { response =>
      IO {
        response.status shouldBe Status.Ok
        val cookieOpt = response.cookies.find(_.name == COOKIE_NAME)
        cookieOpt should be (defined)
        cookieOpt.map(c => RequestCookie(c.name, c.content))
      }
    }.unsafeRunSync()
  }

  Then("the system returns a session cookie for authentication") { () =>
    authCookie shouldBe defined
  }

  When("the user creates a new order with the following data:") { (table: DataTable) =>
    val row = table.asMaps(classOf[String], classOf[String]).asScala.head
    val origin = row.get("Origin")
    val destination = row.get("Destination")
    val weight = row.get("Weight").toDouble

    val newOrder = json"""{
      "userId": $username,
      "origin": $origin,
      "destination": $destination,
      "weight": $weight,
      "departureDate": ${Instant.now().plusSeconds(5).toString}
    }"""

    val createOrderReq = Request[IO](Method.POST, apiRoot / "orders")
      .withEntity(newOrder)
      .addCookie(authCookie.get)

    lastStatus = client.status(createOrderReq).unsafeRunSync()

    Thread.sleep(1000)
  }

  Then("the order creation request is accepted") { () =>
    withClue(s"Order creation failed. Status: $lastStatus") {
      lastStatus shouldBe Status.Accepted
    }
  }

  When("the user requests the list of their orders") { () =>
    val getOrdersReq = Request[IO](Method.GET, apiRoot / "orders")
      .addCookie(authCookie.get)

    val response = client.expect[Json](getOrdersReq).unsafeRunSync()
    response.isArray shouldBe true
    ordersList = response.asArray.getOrElse(Vector.empty)
  }

  Then("the received orders list is not empty") { () =>
    ordersList should not be empty
  }

  Then("the first order belongs to the user") { () =>
    val userIdField = ordersList.head.hcursor.get[String]("usrId").toOption
    userIdField shouldBe Some(username)
  }

  Then("the first order has {string} as destination") { (dest: String) =>
    val destField = ordersList.head.hcursor.get[String]("destination").toOption
    destField shouldBe Some(dest)
  }

  When("the user performs the final logout") { () =>
    val finalLogoutReq = Request[IO](Method.POST, apiRoot / "logout")
      .addCookie(authCookie.get)

    lastStatus = client.status(finalLogoutReq).unsafeRunSync()
  }

  Then("the disconnection is successful") { () =>
    lastStatus shouldBe Status.Ok
  }
}