package com.timetrade.eas.tools

case class Account(
  licensee: String,
  emailAddress: String,
  externalID: String,
  domain: String = "",
  username: String,
  password: Option[String],
  mailHost: String,
  notifierURI: String,
  // Base64-encode byte array:
  certificate: Option[String] = None,
  certificatePassphrase: Option[String] = None,
  mailServerType: String = "MsExchange",
  tentativeMeansFreeInFreeBusy: Boolean = false,
  storeFields: Array[String] = Array(),
  bodyTemplate: String = ""
) {
  require(
    isSupplied(password)
    ||
    isSupplied(certificate) && isSupplied(certificatePassphrase),
    "Account must have either a password or a certificate and passphrase"
  )

  private def isSupplied(os: Option[String]) = {
    os.isDefined && !os.get.isEmpty
  }

  def toCredentials: Credentials =
    Credentials(mailHost,
                username,
                password,
                domain,
                certificate,
                certificatePassphrase)
}
