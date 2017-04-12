package com.metl.utils

import net.liftweb.common.Logger
import net.liftweb.common.Full

case class SimpleMailer(smtp:String,port:Int,ssl:Boolean,username:String,password:String,fromAddress:Option[String] = None,recipients:List[String]) extends net.liftweb.util.Mailer with Logger {
  import net.liftweb.util.Mailer._
  customProperties = Map(
    "mail.smtp.starttls.enable" -> ssl.toString,
    "mail.smtp.host" -> smtp,
    "mail.smtp.port" -> port.toString,
    "mail.smtp.auth" -> "true"
  )
  authenticator = Full(new javax.mail.Authenticator {
    override def getPasswordAuthentication = new javax.mail.PasswordAuthentication(username,password)
  })
  def sendMailMessage(subject:String,message:String):Unit = {
    try {
      sendMail(From(fromAddress.getOrElse("no-reply-metl@stackableregiments.com")),Subject(subject),PlainMailBodyType(message) :: recipients.map(r => To(r)):_*)
    }
    catch {
      case e:Throwable => {
        error("exception while sending mail: %s".format(e.getMessage),e)
      }
    }
  }
}

