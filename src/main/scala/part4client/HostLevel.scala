package part4client
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}

object HostLevel extends App with PaymentJsonProtocol with DefaultJsonProtocol {
  import PaymentSystemDomain._

  implicit val system = ActorSystem("HostLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) => {
        // VERY IMPORTANT - otherwise you're leaking connections from your connection pool
        response.discardEntityBytes()
        s"Request $value has received response: $response"
      }
      case (Failure(ex), value) => {
        s"Request $value has failed: $ex"
      }
    }
    .runWith(Sink.foreach[String](println))

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("1234-1234-4321-4321", "432", "my-awesome-account")
  )

  val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account", 99))
  val serverHttpRequest = paymentRequest.map(paymentRequest =>
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequest)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) => println(s"The order ID: $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) => println(s"The order id $orderId was successful and returned the response: $response")
      case (Failure(ex), orderId) => println(s"The order id $orderId could not be completed: $ex")
    }

  // Should use the hot-level API for High-volum, low-latency request
}
