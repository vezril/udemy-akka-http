package part2lowlevelserver
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink }

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object LowLevelApi extends App {

  implicit val system       = ActorSystem("LowLevelApi")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from: ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) => {
      println(s"Server binding successful")
      binding.terminate(2 seconds)
    }
    case Failure(ex) => println(s"Server binding failed: $ex")
  }

  /*
    Method 1: synchronously serve HTTP responses
   */

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) => {
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
        )
      )
    }
    case request: HttpRequest => {
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Oops! The resource can't be found.
            | </body>
            |</html>
          """.stripMargin
        )
      )
    }
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  //Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  //Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /*
    Method 3: serve back HTTP responses ASYNCHRONOUSLY
   */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => { // method, URI, HTTP headers, content, protocol (HTTP1.1/HTTP2.0)
      Future(
        HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
          )
        )
      )
    }
    case request: HttpRequest => {
      request.discardEntityBytes()
      Future(
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
            |<html>
            | <body>
            |   Oops! The resource can't be found.
            | </body>
            |</html>
          """.stripMargin
          )
        )
      )
    }
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // streams-based "manual" version
  //Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)

  // shorthand version
  //Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  /*
    Method 3: async via Akka streams
   */

  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => { // method, URI, HTTP headers, content, protocol (HTTP1.1/HTTP2.0)
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html>
            """.stripMargin
        )
      )
    }
    case request: HttpRequest => {
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
              |<html>
              | <body>
              |   Oops! The resource can't be found.
              | </body>
              |</html>
            """.stripMargin
        )
      )
    }
  }

  // Manual version
//  Http().bind("localhost", 8082).runForeach { connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }

  // shorthand version
  //Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

  /**
    * Exercise: create your own HTTP server running on localhost on port 8388 , which replies
    *   - with a welcome message on the "front door" localhost:8388
    *   - with a proper HTML on localhost:8388/about
    *   - with a 404 message otherwise
    */
  val syncExerciseHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) => {
      HttpResponse(
        // status code OK (200) is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "Hell from the exercise front door!"
        )
      )
    }
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) => {
      HttpResponse(
        // status code OK (200) is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   <div style="color: red">
            |     Hello from the about page
            |   <div>
            | </body>
            |</html>
          """.stripMargin
        )
      )
    }

    // path /search redirects to some other part of your website
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) => {
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )
    }

    case request: HttpRequest => {
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "You're in no mans land, sorry"
        )
      )
    }
  }

  val bindingFuture = Http().bindAndHandleSync(syncExerciseHandler, "localhost", 8388)

  // shutdown the server:
  bindingFuture
    .flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())

}
