package com.timetrade.eas.tools

import scala.util.parsing.combinator.RegexParsers

object CSVParser extends RegexParsers {
  def apply(f: java.io.File): Iterator[List[String]] = io.Source.fromFile(f).getLines().map(apply(_))
  def apply(s: String): List[String] = parseAll(fromCsv, s) match {
    case Success(result, _) => result
    case failure: NoSuccess => {throw new Exception("Parse Failed")}
  }

  def fromCsv:Parser[List[String]] = rep1(mainToken) ^^ {case x => x}
  def mainToken = (doubleQuotedTerm | singleQuotedTerm | unquotedTerm) <~ ",?".r ^^ {case a => a}
  def doubleQuotedTerm: Parser[String] = "\"" ~> "[^\"]+".r <~ "\"" ^^ {case a => (""/:a)(_+_)}
  def singleQuotedTerm = "'" ~> "[^']+".r <~ "'" ^^ {case a => (""/:a)(_+_)}
  def unquotedTerm = "[^,]+".r ^^ {case a => (""/:a)(_+_)}

  override def skipWhitespace = false
}
