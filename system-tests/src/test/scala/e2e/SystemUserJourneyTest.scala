package e2e

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.literal.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.JavaNetClientBuilder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class SystemUserJourneyTest extends AnyFlatSpec with Matchers:

  private val gatewayBaseUrl = sys.env.getOrElse("API_GATEWAY_URL", "http://localhost:8080")
  private val apiRoot = Uri.unsafeFromString(gatewayBaseUrl) / "test"
  private val COOKIE_NAME = "drone_auth_session"

  "The Drone Delivery System" should "allow a full user journey (Register -> Login -> Order -> View Orders -> Logout)" in:

    JavaNetClientBuilder[IO].resource.use { client =>
    IO {
      val username = s"user_${System.currentTimeMillis()}"
      val password = "password123"

      println(s"Inizio User Journey per l'utente: $username")
      val credentialsJson = json"""{
          "username": $username,
          "password": $password
        }"""

      // --- STEP 0: LOGOUT (Pulizia preliminare) ---
      println("0. Logout...")
      val logoutReq = Request[IO](Method.POST, apiRoot / "logout")
      client.run(logoutReq).use { _ => IO.unit }.unsafeRunSync()

      // --- STEP 1: REGISTRAZIONE ---
      println("1. Registrazione utente...")
      val registerReq = Request[IO](Method.POST, apiRoot / "register").withEntity(credentialsJson)
      val regStatus = client.status(registerReq).unsafeRunSync()

      withClue(s"Registrazione fallita con status $regStatus") {
        regStatus should (be(Status.Created) or be(Status.Conflict))
      }

      // --- STEP 2: LOGIN ---
      println("2. Login utente...")
      val loginReq = Request[IO](Method.POST, apiRoot / "login").withEntity(credentialsJson)
      val authCookie: RequestCookie = client.run(loginReq).use { response =>
        IO {
          response.status shouldBe Status.Ok
          val cookieOpt = response.cookies.find(_.name == COOKIE_NAME)
          cookieOpt should be (defined)
          val responseCookie = cookieOpt.get
          RequestCookie(responseCookie.name, responseCookie.content)
        }
      }.unsafeRunSync()

      println(s"   -> Login OK, Cookie ricevuto: $authCookie")

      // --- STEP 3: CREAZIONE ORDINE ---
      println("3. Creazione ordine...")
      val newOrder = json"""{
          "userId": $username,
          "origin": "Magazzino A",
          "destination": "Cliente B",
          "weight": 5.5,
          "departureDate": ${Instant.now().plusSeconds(5).toString}
        }"""

      val createOrderReq = Request[IO](Method.POST, apiRoot / "orders")
        .withEntity(newOrder)
        .addCookie(authCookie)
      val orderStatus = client.status(createOrderReq).unsafeRunSync()

      withClue(s"La creazione ordine fallita. Status: $orderStatus") {
        orderStatus shouldBe Status.Accepted
      }

      Thread.sleep(1000)

      // --- STEP 4: RECUPERO ORDINI ---
      println("4. Verifica lista ordini...")
      val getOrdersReq = Request[IO](Method.GET, apiRoot / "orders")
        .addCookie(authCookie)

      val response = client.expect[Json](getOrdersReq).unsafeRunSync()
      response.isArray shouldBe true

      val ordersList = response.asArray.getOrElse(Vector.empty)
      println(s"   -> Trovati ${ordersList.size} ordini.")

      ordersList should not be empty

      val firstOrder = ordersList.head
      val userIdField = firstOrder.hcursor.get[String]("usrId").toOption
      val destField = firstOrder.hcursor.get[String]("destination").toOption

      userIdField shouldBe Some(username)
      destField shouldBe Some("Cliente B")

      // --- STEP 5: LOGOUT ---
      println("5. Logout finale...")
      val finalLogoutReq = Request[IO](Method.POST, apiRoot / "logout")
        .addCookie(authCookie)

      val logoutStatusFinal = client.status(finalLogoutReq).unsafeRunSync()
      logoutStatusFinal shouldBe Status.Ok

    }
  }.unsafeRunSync()