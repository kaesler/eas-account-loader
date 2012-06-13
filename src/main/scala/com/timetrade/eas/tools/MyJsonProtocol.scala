package com.timetrade.eas.tools

import cc.spray.json._

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val accountFormat = jsonFormat14(Account)
}