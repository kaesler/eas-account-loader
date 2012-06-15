package com.timetrade.eas.tools

import java.io.File
import java.net.URL
import java.net.URLEncoder

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.Await
import akka.dispatch.Future
import akka.util.duration.intToDurationInt

import cc.spray.can.client.HttpClient
import cc.spray.client.DispatchStrategies
import cc.spray.client.HttpConduit
import cc.spray.client.Post
import cc.spray.http.MediaTypes.ApplicationMediaType
import cc.spray.http.BasicHttpCredentials
import cc.spray.http.HttpResponse
import cc.spray.http.MediaTypes
import cc.spray.http.StatusCodes
import cc.spray.io.IoWorker

import Marshallers._

/**
 * Tool to create EAS connector accounts.
 */
object EasAccountLoader {

  // The media type for Account.
  val `application/vnd.timetrade.calendar-connect.account+json` =
    MediaTypes.register(
      new ApplicationMediaType("vnd.timetrade.calendar-connect.account+json"))

  val requestTimeoutInSeconds = 60

  type OptionMap = Map[Symbol, Any]

  def main(args: Array[String]) = {

    val options = parseAndValidateArgs(args)

    val accounts = parseCsvFile(new File(options('csvFile).asInstanceOf[String]))
    //accounts foreach { acc => println(acc.toJson) }

    val serviceUrl = new URL(options('url).asInstanceOf[String])
    createAccounts(serviceUrl, accounts)
  }

  // --------------------------------------------------------------------//

  def createAccounts(serviceUrl: URL, accounts: List[Account]) = {

    // Initialize Akka and Spray pieces.
    implicit val system = ActorSystem()

    def log = system.log

    // Every spray-can HttpClient (and HttpServer) needs an IoWorker for low-level network IO
    // (but several servers and/or clients can share one)
    val ioWorker = new IoWorker(system).start()

    // Create config settings needed to condition the HttpClient.
    val scheme = serviceUrl.getProtocol
    val configText =
      ("spray.can.client.request-timeout = %d s\n" +
      "spray.can.client.idle-timeout = %d s\n" +
      "spray.can.client.ssl-encryption = %s\n").format(
        requestTimeoutInSeconds,
        requestTimeoutInSeconds,
        (serviceUrl.getProtocol == "https").toString)

    println(configText)
    // Create and start a spray-can HttpClient
    val httpClient = system.actorOf(
      props =
        Props(
          new HttpClient(
            ioWorker,
            ConfigFactory.parseString(configText))),
      name = "http-client"
    )

    val host = serviceUrl.getHost
    val port = (if (serviceUrl.getPort > 0) serviceUrl.getPort else 80)

    val conduit =
      new HttpConduit(
        httpClient, host, port,
        DispatchStrategies.Pipelined,
        config = ConfigFactory.parseString("spray.client.max-retries=0")) {

      val adminLoginId = "ec7e63ddbbdd4a1a850d54c0012cbd68"
      val password = "071b28636d1a46bc8b1ca8c82d85a25e"

      // : SimpleRequest[Account] => Future[HttpResponse]
      val pipeline = (
        simpleRequest[Account]
        ~> authenticate(BasicHttpCredentials(adminLoginId, password))
        ~> sendReceive
      )
    }

    // Run all the creations in parallel
    val futures = accounts
      .map { acc =>
        val uri = serviceUrl.toString +
          "/api/%s/calendars".format(URLEncoder.encode(acc.licensee, "UTF-8"))
        println("Sending POST for " + acc.emailAddress)
        conduit.pipeline(Post(uri, acc))
    }

    // Wait till they all finish.
    val future = Future.sequence(futures)
    Await.result(future, (5 * futures.size) seconds)

    // Zip the accounts with the results and examine outcomes.
    accounts
      .zip(futures.map (_.value))
      .foreach { pair =>
        val (acc, optResult) = pair
        optResult match {
          case None => println("No response for %s".format(acc.emailAddress))
          case Some(result) => {
            result match {
              case Left(throwable) => {
                println(
                  "Problem creating account for %s:\n%s".format(
                    acc.emailAddress,
                    throwable.getStackTrace))
              }
              case Right(response) => printResponse(acc, response)
            }
          }
        }
      }

    conduit.close()
    system.shutdown()
    ioWorker.stop()
  }

  private def printResponse(acc: Account, response: HttpResponse) = {
    if (response.status == StatusCodes.Created) {
      // Success
      val location =
        response.headers
          .find { header => header.name == "location"}
          .getOrElse("<unknown>")
      println("\n>>>>>>>>>>>>>>>>>>>  Calendar created at %s\n".format(location))
    } else {
      println(
        "\n!!!!!!!!!!!!!!!!!!!!  Failed to create calendar for %s: %d\n"
          .format(acc.emailAddress, response.status.value))
    }
  }

  private def parseCsvFile(file: File): List[Account] = {
    val contents = CSVParser(file).toList

    // Check that all lines had the expected number of fields
    val expectedFieldCount = 7
    if (contents exists { _.size != expectedFieldCount}) {
      fail("CSV file has whose field count is not %d".format(expectedFieldCount) )
    }

    contents map { fields =>
      Account(fields(0),fields(1),fields(2),fields(3),Some(fields(4)),fields(5),fields(6))
    }
  }

  private def fail(msg: String) = {
    println(msg)
    sys.exit(-1)
  }

  private def parseAndValidateArgs(args: Array[String]): OptionMap = {
    val result = parseRemainingArgs(Map(), args.toList)
    validate(result)
    result
  }

  private def parseRemainingArgs(map: OptionMap, args: List[String]): OptionMap = {
    args match {
      case Nil => map

      case "--csv-file" :: value :: tail =>
        parseRemainingArgs(map ++ Map('csvFile -> value), tail)

      case "--url" :: value :: tail =>
        parseRemainingArgs(map ++ Map('url -> value), tail)

      case option :: tail =>
        println("Unknown option "+option)
      printUsage
      sys.exit(1)
    }
  }

  private def validate(options: OptionMap) = {
    if (!options.contains('csvFile))
      commandLineError("")
    if (!options.contains('url))
      commandLineError("")


    val f = new File(options('csvFile).asInstanceOf[String])
    if (!f.exists)
      commandLineError("File does not exist: %s".format(f.getPath))
    if (!f.isFile)
      commandLineError("File is not a file: %s".format(f.getPath))

    val u = options('url).asInstanceOf[String]
    try {
      val url = new URL(u)
      validateUrl(url)
    } catch {
      case e: Exception => commandLineError("Invalid URL: %s".format(u))
    }
  }

  private def validateUrl(url: URL): Unit = {
    val acceptableProtocols = Set("http")
    val protocol = url.getProtocol
    val path = url.getPath

    if ((! acceptableProtocols.contains(protocol))
        ||
        (path != null && !path.isEmpty)) {
          commandLineError(
            "URL should be of the form: http://HOST[:PORT]. Was: "
            + url.toString())
        }
  }

  private def commandLineError(msg:String) = {
    if (!msg.isEmpty)
      println(msg)
    printUsage
    sys.exit(1)
  }

  lazy val expectedJarName = "eas-account-loader.jar"

  private def printUsage = {
    println(
      ("Usage: java -jar %s" +
       " --csv-file CSVFILE" +
       " --url URL"
     ).format(expectedJarName))
    println("  where\n" +
            "    URL is the location of the EAS connector in the form http://HOST[:PORT] \n" +
            "    CSVFILE is a csv-formatted file containing account details formatted like this:\n" +
            "      \"licensee,emailAddress,externalID,username,password,mailHost,notifierURI\"\n" +
            "      in which lines beginning with '#' are ignored."
          )
  }
}
