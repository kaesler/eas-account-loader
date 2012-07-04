package com.timetrade.eas.tools.accountloader

import cc.spray.http.MediaTypes.ApplicationMediaType
import cc.spray.http.MediaTypes

/**
 * Media types peculiar to this application.
 */
object CustomMediaTypes {

  // The media type for Account.
  val `application/vnd.timetrade.calendar-connect.account+json` =
    MediaTypes.register(
      new ApplicationMediaType("vnd.timetrade.calendar-connect.account+json"))

  // The media type for Credentials.
  val `application/vnd.timetrade.calendar-connect.credentials-validation+json` =
    MediaTypes.register(
      new ApplicationMediaType("vnd.timetrade.calendar-connect.credentials-validation+json"))
}