package com.timetrade.eas.tools.accountloader

import java.io.File
import java.net.URL
import java.net.URLEncoder
import com.typesafe.config.ConfigFactory
import akka.actor.ActorRef
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
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import org.apache.commons.codec.binary.Base64


/**
 * Tool to create EAS connector accounts.
 */
object EasAccountLoader {

  val formatDescription =
    "licensee,emailAddress,externalID,domain,username,password,mailHost,notifierURI,certificateFilePath,certificatePassphrase"

  // The media type for Account.
  val `application/vnd.timetrade.calendar-connect.account+json` =
    MediaTypes.register(
      new ApplicationMediaType("vnd.timetrade.calendar-connect.account+json"))

  val requestTimeoutInSeconds = 60

  type OptionMap = Map[Symbol, Any]

  val adminLoginId = "ec7e63ddbbdd4a1a850d54c0012cbd68"
  val password = "071b28636d1a46bc8b1ca8c82d85a25e"

  def main(args: Array[String]) = {

    val options = parseAndValidateArgs(args)

    val serviceUrl = new URL(options('url).asInstanceOf[String])

    val accounts = parseCsvFile(new File(options('csvFile).asInstanceOf[String]))

    if (!validate(accounts)) {
      sys.exit(-1)
    }
    //accounts foreach { acc => println(acc.toJson) }

    // Initialize Akka and Spray pieces.
    implicit val system = ActorSystem()

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

    // Create and start a spray-can client actor.
    val httpClient = system.actorOf(
      props =
        Props(
          new HttpClient(
            ioWorker,
            ConfigFactory.parseString(configText))),
      name = "http-client")

    try {
      if (validateCredentials(system, httpClient, serviceUrl, accounts)) {
        createAccounts(system, httpClient, serviceUrl, accounts)
      }
    } finally {
      ioWorker.stop()
      system.shutdown()
    }
  }

  // --------------------------------------------------------------------//

  private def validateCredentials(implicit system: ActorSystem,
                                  httpClient: ActorRef,
                                  serviceUrl: URL,
                                  accounts: List[Account]): Boolean = {

    // Create a conduit for running the requests.
    val host = serviceUrl.getHost
    val port = (if (serviceUrl.getPort > 0) serviceUrl.getPort else 80)
    val conduit =
      new HttpConduit(
        httpClient, host, port,
        DispatchStrategies.NonPipelined(),
        config = ConfigFactory.parseString("spray.client.max-retries=0")) {

        // : SimpleRequest[Account] => Future[HttpResponse]
        val pipeline = (
          simpleRequest[Credentials]
          ~> authenticate(BasicHttpCredentials(adminLoginId, password))
          ~> sendReceive
        )
      }

    try {
      // The number of requests we'll run in parallel.
      val BATCH_SIZE = 4

      val uri = serviceUrl.toString + "/api/credentials-validation"

      print("Validating credentials ")

      val batches = accounts
        // Get creds
        .map { _.toCredentials }
        // Form batches.
        .grouped(BATCH_SIZE)

      val futures = batches
        .flatMap { batch =>
          // Run a batch all in parallel.
          val batchFutures = batch.map { creds => conduit.pipeline(Post(uri, creds)) }

          // Wait till all in the batch finish.
          val future = Future.sequence(batchFutures)
          Await.result(future, math.max(60, 10 * batchFutures.size) seconds)

          // Show progress on command line.
          (1 to BATCH_SIZE) foreach { _ => print(".") }

          // Accumulate.
          batchFutures
        }
      println

      // Zip the accounts with the results and examine outcomes.
      val outcomes: List[Boolean] = accounts
        .zip(futures.toList.map (_.value))
        .map { pair =>
          val (acc, optResult) = pair
          val emailAddress = acc.emailAddress
          optResult match {
            case None =>
              println("No response when validating credentials for %s".format(emailAddress))
              false
            case Some(result) => {
              result match {
                case Left(throwable) => {
                  println(
                    "Exception when validating credentials for %s:\n%s".format(
                      emailAddress,
                      throwable.getStackTrace))
                  false
                }
                case Right(response) =>
                  response.content match {
                    case None =>
                      println(
                        "Empty response validating credentials for %s".format(emailAddress))
                      false
                    case Some(content) =>
                      val body = new String(content.buffer, "UTF-8")
                      val fields = body.split("\\|")
                      val code = fields(0)
                      val details = (if (fields.size > 1) fields(1) else "")
                      try {
                        code match {
                          case "OK" => true
                          case "HOST_NOT_FOUND" =>
                            println(
                              "Host %s not found validating credentials for %s"
                                .format(acc.mailHost, emailAddress))
                            false
                          case "BAD_CREDENTIALS" =>
                            println(
                              "Credentials invalid for %s: %s"
                                .format(emailAddress, details))
                            false
                        }
                      } catch {
                        case e: NumberFormatException =>
                          println(
                            "Invalid response validating credentials for %s: %s"
                              .format(emailAddress, body))
                          false
                      }
                  }
              }
            }
          }
        }
      val allOk = outcomes.forall( b => b)
      allOk
    } finally {
      conduit.close()
    }
  }

  private def createAccounts(implicit system: ActorSystem,
                             httpClient: ActorRef,
                             serviceUrl: URL,
                             accounts: List[Account]) = {

    val host = serviceUrl.getHost
    val port = (if (serviceUrl.getPort > 0) serviceUrl.getPort else 80)

    val conduit =
      new HttpConduit(
        httpClient, host, port,
        DispatchStrategies.NonPipelined(),
        config = ConfigFactory.parseString("spray.client.max-retries=0")) {

        // : SimpleRequest[Account] => Future[HttpResponse]
        val pipeline = (
          simpleRequest[Account]
          ~> authenticate(BasicHttpCredentials(adminLoginId, password))
          ~> sendReceive
        )
      }

    try {
      // The number of requests we'll run in parallel.
      val BATCH_SIZE = 4

      print("Creating accounts ")

      val batches = accounts.grouped(BATCH_SIZE)
      val futures = batches
        .flatMap { batch =>

          // Run a batch all in parallel.
          val batchFutures = batch.map { account =>
            val uri = serviceUrl.toString +
              "/api/%s/calendars".format(URLEncoder.encode(account.licensee, "UTF-8"))
            conduit.pipeline(Post(uri, account))
          }

          // Wait for that batch to finish.
          val future = Future.sequence(batchFutures)
          Await.result(future, math.max(120, 120 * batchFutures.size) seconds)

          // Show progress on command line.
          (1 to BATCH_SIZE) foreach { _ => print(".") }

          // Accumulate.
          batchFutures

        }

      // Show progress
      println

      // Zip the accounts with the results and examine outcomes.
      accounts
        .zip(futures.toList.map (_.value))
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
    } finally {
      conduit.close()
    }
  }

  private def printResponse(acc: Account, response: HttpResponse) = {
    if (response.status == StatusCodes.Created) {
      // Success
      val location =
        response.headers
          .find { header => header.name == "location"}
          .getOrElse("<unknown>")
      println("  Calendar created at %s".format(location))
    } else {
      println(
        "  Failed to create calendar for %s: %d\n"
          .format(acc.emailAddress, response.status.value))
    }
  }

  private def parseCsvFile(file: File): List[Account] = {
    val contents = CSVParser(file).toList

    // Check that all lines had the expected number of fields
    val expectedFieldCount = 10
    if (contents exists { _.size != expectedFieldCount}) {
      fail("CSV file format wrong: each line must have %d fields like this: \n  %s"
            .format(expectedFieldCount,
                    formatDescription))
    }

    contents map { fields =>

      Account(
        licensee = fields(0),
        emailAddress = fields(1),
        externalID = fields(2),
        domain = fields(3),
        username = fields(4),
        password = { if (fields(5).isEmpty) None else Some(fields(5)) },
        mailHost = fields(6),
        notifierURI = fields(7),
        certificate = {
          if (fields(8).isEmpty) None else Some(getCertificateBytesAsBase64String(fields(8)))
        },
        certificatePassphrase = { if (fields(9).isEmpty) None else Some(fields(9)) }
      )
    }
  }

  private def validate(accounts: List[Account]): Boolean = {
    accounts forall { acc =>
      val ok = (acc.password.isDefined
                ^ // xor
                (acc.certificate.isDefined && acc.certificatePassphrase.isDefined))
      if (!ok) {
        println(
          "Account for %s invalid: either a password or a certificate and passphrase must be specified."
            .format(acc.emailAddress))
      }
      ok
    }
  }
  private def getCertificateBytesAsBase64String(path: String): String = {
    // Read the file bytes.
    val in = new FileInputStream(path)
    val out = new ByteArrayOutputStream()
    try {
      val buf = new Array[Byte](1024)
      Iterator.continually(in.read(buf))
              .takeWhile(_ != -1)
              .foreach { out.write(buf, 0 , _) }
    } finally {
      in.close
      out.close
    }

    // Base64 encode them
    new Base64(-1).encodeToString(out.toByteArray)
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
    println(("  where\n" +
             "    URL is the location of the EAS connector in the form http://HOST[:PORT] \n" +
             "    CSVFILE is a csv-formatted file containing account details formatted like this:\n" +
             "      \"%s\"\n" +
             "      in which lines beginning with '#' are ignored.\n" +
             "    Domain is optional.\n" +
             "    Either a password or a certificateFile and passphrase must be provided.")
               .format(formatDescription)
          )
  }

  private def time[T](name: String, code : => T) =  {
    val start = System.nanoTime: Double
    val result = code
    val end = System.nanoTime: Double
    println("%s took %f msecs".format(name, (end - start) / 1000000.0))
    result
  }
}
