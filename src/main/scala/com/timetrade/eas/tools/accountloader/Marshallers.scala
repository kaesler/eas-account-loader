package com.timetrade.eas.tools.accountloader

import cc.spray.http.ContentType
import cc.spray.http.HttpContent
import cc.spray.http.MediaTypes._
import cc.spray.typeconversion.SimpleMarshaller
import CustomMediaTypes._
import JsonProtocol._


/**
 * Custom marshalling code.
 */
object Marshallers {

  implicit val accountMarshaller = new SimpleMarshaller[Account] {
    val canMarshalTo =
      ContentType(`application/vnd.timetrade.calendar-connect.account+json`) :: Nil

    def marshal(value: Account, contentType: ContentType) = {
      HttpContent(contentType, JsonProtocol.marshallToJsonString(value).toString)
    }
  }

  implicit val credentialsMarshaller = new SimpleMarshaller[Credentials] {
    val canMarshalTo =
      ContentType(`application/vnd.timetrade.calendar-connect.credentials-validation+json`) :: Nil

    def marshal(value: Credentials, contentType: ContentType) = {
      HttpContent(contentType, JsonProtocol.marshallToJsonString(value).toString)
    }
  }
}