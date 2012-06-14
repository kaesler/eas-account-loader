package com.timetrade.eas.tools

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
}