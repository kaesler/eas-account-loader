package com.timetrade.eas.tools.accountloader

import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.io.FileInputStream
import java.io.ByteArrayOutputStream

import com.typesafe.config.ConfigFactory

import org.apache.commons.codec.binary.Base64

import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.dispatch.Future
import akka.util.duration._

import spray.http.HttpResponse
import spray.http.StatusCodes
import spray.http.HttpRequest
import spray.http.EmptyEntity
import spray.http.HttpBody
import spray.httpx.RequestBuilding._

import Marshallers._

/**
 * Tool to create EAS connector accounts.
 */
object EasAccountLoader {

  type Pipeline = HttpRequest => Future[HttpResponse]

  val formatDescription =
    "licensee,emailAddress,externalID,domain,username,password,mailHost,notifierURI,certificateFilePath,certificatePassphrase"

  type OptionMap = Map[Symbol, Any]

  val adminLoginId = "ec7e63ddbbdd4a1a850d54c0012cbd68"
  val password = "071b28636d1a46bc8b1ca8c82d85a25e"

  // Create an ActorSystem for Spray and Futures
  implicit val system = ActorSystem()

  def main(args: Array[String]) = {

    val options = parseAndValidateArgs(args)

    val serviceUrl = new URL(options('url).asInstanceOf[String])

    val accounts = parseCsvFile(new File(options('csvFile).asInstanceOf[String]))

    if (!validate(accounts)) {
      sys.exit(-1)
    }

    val requester = new Requester(system)
    val pipeline = requester.createPipeline(serviceUrl, adminLoginId, password)

    // The number of requests we'll run in parallel.
    val BATCH_SIZE = 8
    try {
      if (pingServer(pipeline)) {
        if (validateCredentials(pipeline, accounts, BATCH_SIZE)) {
          createAccounts(pipeline, accounts, BATCH_SIZE)
        }
      }
    } finally {
      system.shutdown()
    }
  }

  // --------------------------------------------------------------------//

  private def pingServer(pipeline: Pipeline): Boolean = {
    val path ="/api/about"
    val f = pipeline(Get(path))
    try {
      Await.result(f, 10 seconds)
      true
    } catch {
      case t: Throwable =>
        println("Unable to reach server: " + t.getMessage())
        false
    }
  }

  private def validateCredentials(pipeline: Pipeline,
                              accounts: List[Account],
                              batchSize: Int): Boolean = {

    val path = "/api/credentials-validation"

    print("\nValidating credentials %d at a time".format(batchSize))

    val batches = accounts
      // Get creds
      .map { _.toCredentials }
      // Form batches.
      .grouped(batchSize)

    val futures = batches
      .flatMap { batch =>
        // Run a batch all in parallel.
        val batchFutures = batch.map { creds => pipeline(Post(path, creds)) }

        // Wait till all in the batch finish.
        val future = Future.sequence(batchFutures)
        Await.result(future, math.max(60, 10 * batchFutures.size) seconds)

        // Show progress on command line.
        (1 to batch.size) foreach { _ => print(".") }

        // Accumulate.
        batchFutures
      }
      println(" ")

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
                  response.entity match {
                    case EmptyEntity =>
                      println(
                        "Empty response validating credentials for %s".format(emailAddress))
                        false
                    case HttpBody(_, buffer) =>
                      val body = new String(buffer, "UTF-8")
                      val fields = body.split("\\|")
                      val code = fields(0)
                      val details = (if (fields.size > 1) fields(1) else "")
                      try {
                        code match {
                          case "0" => true
                          case "OK" => true

                          case "1" =>
                            println(
                              "Host %s not found validating credentials for %s"
                                .format(acc.mailHost, emailAddress))
                              false

                          case "HOST_NOT_FOUND" =>
                            println(
                              "Host %s not found validating credentials for %s"
                                .format(acc.mailHost, emailAddress))
                              false

                          case "2" =>
                            println(
                              "Credentials invalid for %s: %s"
                                .format(emailAddress, details))
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
  }

  private def createAccounts(pipeline: Pipeline,
                             accounts: List[Account],
                             batchSize: Int) = {

    print("\nCreating accounts %d at a time".format(batchSize))

    val batches = accounts.grouped(batchSize)
    val futures = batches
      .flatMap { batch =>

        // Run a batch all in parallel.
        val batchFutures = batch.map { account =>
            val path = "/api/%s/calendars".format(URLEncoder.encode(account.licensee, "UTF-8"))
              pipeline(Post(path, account))
          }

        // Wait for that batch to finish.
        val future = Future.sequence(batchFutures)
        Await.result(future, math.max(120, 120 * batchFutures.size) seconds)

        // Show progress on command line.
        (1 to batch.size) foreach { _ => print(".") }

        // Accumulate.
        batchFutures
      }

    // Show progress
    println(" ")

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
      val usernameSupplied = ! acc.username.isEmpty
      if (!usernameSupplied) {
        println("Account for %s invalid: a username must be specified."
                  .format(acc.emailAddress))
      }
      val passwordOrCertSupplied =
        (acc.password.isDefined
           ^ // xor
           (acc.certificate.isDefined && acc.certificatePassphrase.isDefined))
      if (!passwordOrCertSupplied) {
        println(
          "Account for %s invalid: either a password or a certificate and passphrase must be specified."
            .format(acc.emailAddress))
      }
        usernameSupplied && passwordOrCertSupplied
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
                 "    Username is required.\n" +
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
