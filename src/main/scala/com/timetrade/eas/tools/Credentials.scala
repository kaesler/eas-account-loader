package com.timetrade.eas.tools

case class Credentials (
  mailHost: String,
  username: String,
  password: Option[String],
  domain: Option[String] = None,
  certificate: Option[String] = None,
  certificatePassphrase: Option[String] = None
)
