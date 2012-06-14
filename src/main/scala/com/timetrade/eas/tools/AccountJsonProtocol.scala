package com.timetrade.eas.tools

import cc.spray.json._

trait AccountJsonProtocol extends DefaultJsonProtocol {
  implicit val accountFormat = jsonFormat14(Account)
}

// Needed this to get Marshallers to work.
object AccountJsonProtocol extends AccountJsonProtocol {
  def marshallToJsonString(acc: Account): String = {
    acc.toJson.toString
  }
}
