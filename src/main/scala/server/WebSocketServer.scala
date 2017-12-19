package server

import scala.io.StdIn

/**
  * Implementation taken from Akka documentation.
  * Source: https://github.com/akka/akka-http/blob/v10.0.11/docs/src/test/scala/docs/http/scaladsl/server/WebSocketExampleSpec.scala
  */
object WebSocketServer extends App {

  import akka.actor.ActorSystem
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.model.ws.{Message, TextMessage}
  import akka.http.scaladsl.server.Directives
  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{Flow, Source}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  import Directives._

  val greeterWebSocketService = Flow[Message].collect {
    case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream)
  }

  val route =
    get {
      handleWebSocketMessages(greeterWebSocketService)
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8137)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()

  import system.dispatcher // for the future transformations
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
