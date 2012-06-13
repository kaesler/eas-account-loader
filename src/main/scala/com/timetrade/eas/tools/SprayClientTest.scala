package com.timetrade.eas.tools

import akka.actor.{Props, ActorSystem}
import cc.spray.io.IoWorker
import cc.spray.can.client.HttpClient
import cc.spray.client.{Get, HttpConduit}
import cc.spray.http.{HttpMethods, HttpRequest}
import cc.spray.util._
import com.typesafe.config.ConfigFactory

object SprayClientTest extends App {

  implicit val system = ActorSystem()

  def log = system.log

  // every spray-can HttpClient (and HttpServer) needs an IoWorker for low-level network IO
  // (but several servers and/or clients can share one)
  val ioWorker = new IoWorker(system).start()

  // create and start a spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioWorker,
                                 ConfigFactory.parseString("spray.can.client.ssl-encryption = on"))),
    name = "http-client"
  )

  fetchAndShowGithubDotCom()


  system.shutdown()
  ioWorker.stop()

  def fetchAndShowGithubDotCom() {
    // an HttpConduit gives us access to an HTTP server, it manages a pool of connections
    val conduit = new HttpConduit(httpClient, "github.com", port = 443)

    // send a simple request
    val responseFuture = conduit.sendReceive(HttpRequest(method = HttpMethods.GET, uri = "/"))
    val response = responseFuture.await
    log.info(
      """|Response for GET request to github.com:
         |status : {}
         |headers: {}
         |body   : {}""".stripMargin,
      response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.content
    )
    conduit.close() // the conduit should be closed when all operations on it have been completed
  }
}