package com.timetrade.eas.tools

case class Account(
  licensee: String,
  emailAddress: String,
  externalID: String,
  username: String,
  password: String,
  mailHost: String,
  notifierURI: String,
  domain: String = "",
  certificate: String = "",
  certificatePassphrase: String = "",
  mailServerType: String = "MsExchange",
  tentativeMeansFreeInFreeBusy: Boolean = false,
  storeFields: Array[String] = Array(),
  bodyTemplate: String = ""
)
