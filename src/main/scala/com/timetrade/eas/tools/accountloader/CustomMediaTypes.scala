package com.timetrade.eas.tools.accountloader

import spray.http.MediaTypes
import spray.http.MediaTypes.CustomMediaType

/**
 * Media types peculiar to this application.
 */
object CustomMediaTypes {

  // The media type for Account.
  val `application/vnd.timetrade.calendar-connect.account+json` =
    MediaTypes.register(
      CustomMediaType("application", "vnd.timetrade.calendar-connect.account+json"))

  // The media type for Credentials.
  val `application/vnd.timetrade.calendar-connect.credentials-validation+json` =
    MediaTypes.register(
      new CustomMediaType("application", "vnd.timetrade.calendar-connect.credentials-validation+json"))
}
