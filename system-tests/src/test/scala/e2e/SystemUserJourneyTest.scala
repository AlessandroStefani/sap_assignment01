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

        // --- STEP 1: REGISTRAZIONE ---
        println("1. Registrazione utente...")
        val registerReq = Request[IO](Method.POST, apiRoot / "register").withEntity(credentialsJson)
        val regStatus = client.status(registerReq).unsafeRunSync()

        withClue("La registrazione dovrebbe restituire 201 Created") {
          regStatus shouldBe Status.Created
        }

        // --- STEP 2: LOGIN ---
        println("2. Login utente...")
        val loginReq = Request[IO](Method.POST, apiRoot / "login").withEntity(credentialsJson)
        val loginStatus = client.status(loginReq).unsafeRunSync()

        withClue("Il login dovrebbe restituire 200 OK") {
          loginStatus shouldBe Status.Ok
        }

        // --- STEP 3: CREAZIONE ORDINE ---
        println("3. Creazione ordine...")

        val newOrder = json"""{
          "userId": $username,
          "origin": "Magazzino A",
          "destination": "Cliente B",
          "weight": 5.5,
          "departureDate": ${Instant.now().plusSeconds(3600).toString}
        }"""

        val createOrderReq = Request[IO](Method.POST, apiRoot / "orders").withEntity(newOrder)
        val orderStatus = client.status(createOrderReq).unsafeRunSync()

        withClue("La creazione ordine dovrebbe restituire 202 Accepted") {
          orderStatus shouldBe Status.Accepted
        }

        Thread.sleep(1000)

        // --- STEP 4: RECUPERO ORDINI ---
        println("4. Verifica lista ordini...")
        val getOrdersReq = Request[IO](Method.GET, apiRoot / "orders")
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
        println("5. Logout...")
        val logoutReq = Request[IO](Method.POST, apiRoot / "logout")
        val logoutStatus = client.status(logoutReq).unsafeRunSync()
        logoutStatus shouldBe Status.Ok
      }
    }.unsafeRunSync()