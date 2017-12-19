package client

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future

object Transmitter extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher

  object Rec extends Actor {
    override def receive: Receive = {
      /*
      https://github.com/akka/akka/issues/20096
      https://github.com/akka/akka-http/issues/15
      */
      case TextMessage.Strict(text)             ⇒ println(s"Received signal $text")
      case TextMessage.Streamed(textStream)     ⇒ textStream.runFold("")(_ + _).foreach(msg => println(s"Received streamed signal: $msg"))
      case BinaryMessage.Strict(binary)         ⇒ //Skip
      case BinaryMessage.Streamed(binaryStream) ⇒ binaryStream.runWith(Sink.ignore); //Skip
    }
  }

//    val host = "ws://echo.websocket.org"
  val host = "ws://localhost:8137"

  val sink: Sink[Message, NotUsed] = Sink.actorRef[Message](system.actorOf(Props(Rec)), PoisonPill)

  val source: Source[Message, NotUsed] = Source(List("test1", "test2") map (TextMessage(_)))

  val flow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(WebSocketRequest(host))

  val (upgradeResponse, closed) =
    source
      .viaMat(flow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(sink)(Keep.both) // also keep the Future[Done]
      .run()

  val connected: Unit = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      println("Connected")
    } else {
      println(s"Connection failed: ${upgrade.response.status}")
    }
  }

}