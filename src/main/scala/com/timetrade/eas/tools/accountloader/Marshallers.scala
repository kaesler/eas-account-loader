package com.timetrade.eas.tools.accountloader

import spray.http.HttpBody
import spray.http.MediaTypes._
import spray.httpx.marshalling._

import CustomMediaTypes._
import JsonProtocol._

/**
 * Custom marshalling code.
 */
object Marshallers {

  implicit val accountMarshaller =
    Marshaller.of[Account](`application/vnd.timetrade.calendar-connect.account+json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpBody(contentType, marshallToJsonString(value)))
    }

  implicit val credentialsMarshaller =
    Marshaller.of[Credentials](`application/vnd.timetrade.calendar-connect.credentials-validation+json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpBody(contentType, marshallToJsonString(value)))
    }
}
