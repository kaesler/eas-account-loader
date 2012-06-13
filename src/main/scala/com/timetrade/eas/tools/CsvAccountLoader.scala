package com.timetrade.eas.tools

import java.io.File
import cc.spray.json._
import MyJsonProtocol._
import java.net.URL

object CsvAccountLoader extends App {
  type OptionMap = Map[Symbol, Any]

//  val f = new File("/Users/kesler/apps/github/repos/tt/csv-account-loader/src/main/scala/com/timetrade/eas/tools/accounts.csv")
//  val x = CSVParser(f).toList
//  println(x)
//  val acc = Account(licensee = "timetrade",
//                    emailAddress = "kesler@foo",
//                    externalID = "",
//                    username = "",
//                    password = "",
//                    mailHost = "",
//                    notifierURI = ""
//            ).toJson
//  println(acc)

  /////////////////////// Main code
  val options = parseAndValidateArgs(args)

  val accounts = parseCsvFile(new File(options('csvFile).asInstanceOf[String]))
  accounts foreach { acc => println(acc.toJson) }

  ////////////////////////////////////////////////////////////////////

  private def parseCsvFile(file: File): List[Account] = {
    val fullContents = CSVParser(file).toList
    val firstField = fullContents.head.head

    // Skip leading lines beginning with "#"
    val contents = fullContents dropWhile { fields =>  fields.head.startsWith("#") }

    // Check that all lines had the expected number of fields
    val expectedFieldCount = 7
    if (contents exists { _.size != expectedFieldCount}) {
      fail("CSV file has whose field count is not %d".format(expectedFieldCount) )
    }

    contents map { fields =>
      Account(fields(0),fields(1),fields(2),fields(3),fields(4),fields(5),fields(6))
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
    } catch {
      case e: Exception => commandLineError("Invalid URL: %s".format(u))
    }
  }

  private def commandLineError(msg:String) = {
    if (!msg.isEmpty)
      println(msg)
    printUsage
    sys.exit(1)
  }

  val expectedJarName = "eas-loader.jar"

  private def printUsage = {
    println(
      ("Usage: java -jar %s" +
       " --csv-file CSVFILE" +
       " --url URL"
    ).format(expectedJarName))
    println("  where\n" +
            "    CSVFILE is a csv-format file containing account details\n" +
            "    URL \n"
            )
  }
}
