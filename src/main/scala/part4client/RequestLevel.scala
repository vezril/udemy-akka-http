package part4client
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object RequestLevelApi extends App {

  implicit val system = ActorSystem("RequestLevelApi")
  implicit val materializer = ActorMaterializer()

}
