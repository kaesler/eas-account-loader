package com.timetrade.eas.tools.accountloader

import cc.spray.json._

object JsonProtocol extends DefaultJsonProtocol {
  implicit val accountFormat = jsonFormat14(Account)
  implicit val credentialsFormat = jsonFormat6(Credentials)

  //Needed these to get Marshallers to work.
  def marshallToJsonString(acc: Account): String = {
    acc.toJson.toString
  }
  def marshallToJsonString(creds: Credentials): String = {
    creds.toJson.toString
  }
}
