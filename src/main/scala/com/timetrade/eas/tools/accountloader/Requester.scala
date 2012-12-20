package com.timetrade.eas.tools.accountloader

import java.net.URL

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.Future

import spray.can.client.ClientSettings.apply
import spray.can.client.HttpClient
import spray.client.DispatchStrategies
import spray.client.HttpConduit
import spray.client.HttpConduit._
import spray.http.BasicHttpCredentials
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.io.IOExtension

/**
 * This class encapsulates the Spray HTTP request mechanism.
 */
class Requester(system: ActorSystem) {
  type Pipeline = HttpRequest => Future[HttpResponse]

  val requestTimeoutInSeconds = 60

  val ioBridge = IOExtension(system).ioBridge()

  def createPipeline(serviceUrl: URL, userid: String, password: String): Pipeline = {
    // Create config settings needed to condition the HttpClient.
    val scheme = serviceUrl.getProtocol
    val configText =
      ("spray.can.client.request-timeout = %d s\n" +
         "spray.can.client.idle-timeout = %d s\n" +
         "spray.can.client.ssl-encryption = %s\n").format(
        requestTimeoutInSeconds,
        requestTimeoutInSeconds,
        (serviceUrl.getProtocol == "https").toString)

    // Create and start a spray-can client actor.
    val httpClient = system.actorOf(
        props =
          Props(
            new HttpClient(
              ioBridge,
              ConfigFactory.parseString(configText))),
        name = "http-client")

    // Create a conduit for running the requests.
    val host = serviceUrl.getHost
    val port = (if (serviceUrl.getPort > 0) serviceUrl.getPort else 80)
    val needsSsl = serviceUrl.getProtocol == "https"
    val conduit = system.actorOf(
        props = Props(new HttpConduit(httpClient,
                                      host,
                                      port,
                                      needsSsl,
                                      DispatchStrategies.NonPipelined())))
      // Create our request pipeline.
    val pipeline: HttpRequest => Future[HttpResponse] = (
      addCredentials(BasicHttpCredentials(userid, password))
        ~> sendReceive(conduit)
    )

    pipeline
  }
}
