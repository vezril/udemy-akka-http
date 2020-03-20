package part3highlevelserver
import akka.actor.ActorSystem
import akka.http.javadsl.server.MissingQueryParamRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ MethodRejection, Rejection, RejectionHandler }

import scala.concurrent.duration._

object HandlingRejections extends App {

  implicit val system       = ActorSystem("HandlingRejection")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter('id) { _ => complete(StatusCodes.OK)
        }
    }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"Encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"Encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) { // Handle rejections from the top level
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) { // handle rejections WITHIN
              parameter('myParam) { _ => complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  //Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8080)

  implicit val customRejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected method")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a missing query rejection: $m")
        complete("Rejected query param")
    }
    .result()

  Http().bindAndHandle(simpleRoute, "localhost", 8080)

}
