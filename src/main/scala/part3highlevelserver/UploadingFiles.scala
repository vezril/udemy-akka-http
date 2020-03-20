package part3highlevelserver
import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, Multipart }
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object UploadingFiles extends App {

  implicit val system       = ActorSystem("UploadingFiles")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val fileRoute =
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   <form action="http://localhost:8000/upload" method="post" enctype="multipart/form-data">
            |     <input type="file" name="myFile">
            |     <button type="submit">Upload</button>
            | </body>
            |</html>
          """.stripMargin
        )
      )
    } ~
      (path("upload") & extractLog) { log =>
        // handle uploading files
        // multipart/form-data
        entity(as[Multipart.FormData]) { formData =>
          // handle file payload
          val partsSource: Source[Multipart.FormData.BodyPart, Any] = formData.parts
          val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] =
            Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
              if (bodyPart.name == "myFile") {
                // create a file
                val filename = "src/main/resources/download/" + bodyPart.filename.getOrElse(
                  "tempFile_" + System.currentTimeMillis()
                )
                val file = new File(filename)

                log.info(s"Writing to file: $filename")

                val fileContentsSource: Source[ByteString, _] = bodyPart.entity.dataBytes
                val fileContentsSink: Sink[ByteString, _]     = FileIO.toPath(file.toPath)

                // Writingg the data to the file
                fileContentsSource.runWith(fileContentsSink)
              }
            }

          val writeOperationFuture = partsSource.runWith(filePartsSink)
          onComplete(writeOperationFuture) {
            case Success(value) => complete("File uploaded")
            case Failure(ex)    => complete(s"File failed to upload: $ex")
          }
        }
      }

  Http().bindAndHandle(fileRoute, "localhost", 8080)
}
