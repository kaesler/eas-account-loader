package com.timetrade.eas.tools

import java.io.File
import cc.spray.json._

import MyJsonProtocol._

object CsvAccountLoader extends App {
  type OptionMap = Map[Symbol, Any]

  // Main code
  val f = new File("/Users/kesler/apps/github/repos/tt/csv-account-loader/src/main/scala/com/timetrade/eas/tools/accounts.csv")
  val x = CSVParser(f).toList
  println(x)
  val acc = Account(licensee = "timetrade",
                    emailAddress = "kesler@foo",
                    externalID = "",
                    username = "",
                    password = "",
                    mailHost = "",
                    notifierURI = ""
            ).toJson
  println(acc)

  //parseAndValidateArgs(args)

  ////////////////////////////////////////////////////////////////////
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
