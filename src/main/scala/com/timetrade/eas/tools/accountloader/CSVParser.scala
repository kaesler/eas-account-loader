package com.timetrade.eas.tools.accountloader

import scala.util.parsing.combinator.RegexParsers

/**
 * Simple parser for CSV files.
 * Produces Iterator[List[String]]
 */
object CSVParser extends RegexParsers {

  def apply(f: java.io.File): Iterator[List[String]] =
    io.Source.fromFile(f)
      .getLines()
      // Remove comment lines
      .filter(!_.startsWith("#"))
      .map(apply(_))

  // Reminder:
  //  ~> combines two parsers and keeps the right-hand result
  //  <~ combines two parsers and keeps the left-hand result
  //  <~ has lower operator precedence than ~ or ~>.

  def apply(s: String): List[String] =
    // Append a comma to the line so that all fields are expected to end in a comma.
    // This allows us to parse empty fields in the various terms using e.g. "[^,]*".r
    // without looping forever.
    parseAll(fromCsv, s + ",") match {
      case Success(result, _) => result
      case failure: NoSuccess => {throw new Exception("Parse Failed")}
  }

  def fromCsv:          Parser[List[String]] = rep1(mainToken) ^^ {case x => x}

  def mainToken:        Parser[String] = (doubleQuotedTerm
                                          | singleQuotedTerm
                                            // Here we insist every field ends in a comma
                                          | unquotedTerm) <~ ",".r ^^ {case a => a}

  def doubleQuotedTerm: Parser[String] = "\"" ~> "[^\"]*".r <~ "\"" ^^ {case a => ("" /: a)(_+_)}

  def singleQuotedTerm: Parser[String] = "'" ~> "[^']*".r <~ "'" ^^ {case a => ("" /: a)(_+_)}

  def unquotedTerm:     Parser[String] = "[^,]*".r ^^ {case a => ("" /: a)(_+_)}

  override def skipWhitespace = false
}

object TestIt extends App {
  List(
    "a,b",
    "a,,b,,c,",
    "'',a,b"
      )
    .foreach { s => println(CSVParser(s)) }
}
