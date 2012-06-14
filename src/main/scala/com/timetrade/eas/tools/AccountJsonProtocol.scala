package com.timetrade.eas.tools

import cc.spray.json._

object AccountJsonProtocol extends DefaultJsonProtocol {
  implicit val accountFormat = jsonFormat14(Account)

  //Needed this to get Marshallers to work.
  def marshallToJsonString(acc: Account): String = {
    acc.toJson.toString
  }
}

//object AccountJsonProtocol extends AccountJsonProtocol {
//}
