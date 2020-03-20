package part4client
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer

import scala.util.{Failure, Success}

object RequestLevel extends App {

  implicit val system = ActorSystem("RequestLevelApi")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))
  responseFuture.onComplete {
    case Success(response) => {
      response.discardEntityBytes()
      println(s"The request was successful and returned: $response")
    }
    case Failure(ex) => println(s"The request was a failure and returned: $ex")
  }

  
}
