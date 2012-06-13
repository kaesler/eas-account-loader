package com.timetrade.eas.tools

case class Account(
  licensee: String,
  emailAddress: String,
  externalID: String,
  username: String,
  password: Option[String],
  mailHost: String,
  notifierURI: String,
  domain: Option[String] = None,
  certificate: Option[String] = None,
  certificatePassphrase: Option[String] = None,
  mailServerType: String = "MsExchange",
  tentativeMeansFreeInFreeBusy: Boolean = false,
  storeFields: Array[String] = Array(),
  bodyTemplate: String = ""
)
