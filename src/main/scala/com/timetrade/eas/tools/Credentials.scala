package com.timetrade.eas.tools

case class Credentials (
  mailHost: String,
  username: String,
  password: Option[String],
  domain: String = "",
  certificate: Option[String] = None,
  certificatePassphrase: Option[String] = None
)
