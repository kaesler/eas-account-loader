package com.timetrade.eas.tools

import cc.spray.http.ContentType
import cc.spray.http.HttpContent
import cc.spray.http.MediaTypes._
import cc.spray.typeconversion.{ Marshaller, SimpleMarshaller }

import CustomMediaTypes._

/**
 * Custom marshalling code.
 */
object Marshallers {

  implicit val accountMarshaller = new SimpleMarshaller[Account] {
    val canMarshalTo =
      ContentType(`application/vnd.timetrade.calendar-connect.account+json`) :: Nil

    def marshal(value: Account, contentType: ContentType) = {
      HttpContent(contentType, AccountJsonProtocol.marshallToJsonString(value).toString)
    }
  }
}