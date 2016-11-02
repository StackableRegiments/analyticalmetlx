package com.metl.model

import net.liftweb.http._
import net.liftweb.http.SHtml._
import net.liftweb.common._
import S._
import net.liftweb.util._
import Helpers._
import scala.xml._
import scala.collection.mutable.{Map=>MutableMap}

case class ClientConfiguration(xmppDomain:String,xmppUsername:String,xmppPassword:String,imageUrl:String)

abstract class ConfigurationProvider extends Logger {
  val keys:MutableMap[String,String] = MutableMap.empty[String,String]
  keys.update("t","ejPass")
  def checkPassword(username:String,password:String):Boolean = {
    debug("checking: %s %s in %s".format(username,password,keys))
    keys.get(username.trim.toLowerCase()).exists(_ == password)
  }
  def getPasswords(username:String):Option[Tuple4[String,String,String,String]] = {
    val xu = adornUsernameForEjabberd(username.trim.toLowerCase())
    val hu = adornUsernameForYaws(username.trim.toLowerCase())

    val xp = keys.get(xu) match {
      case None => {
        val np = generatePasswordForEjabberd(xu)
        keys.update(xu,np)
        Some(np)
      }
      case some => some
    }
    val hp = keys.get(hu) match {
      case None => {
        val np = generatePasswordForYaws(hu)
        keys.update(hu,np)
        Some(np)
      }
      case some => some
    }
    for (
      x <- xp;
      h <- hp
    ) yield {
      (xu,x,hu,h)
    }
  }
  protected def generatePasswordForYaws(username:String):String
  protected def generatePasswordForEjabberd(username:String):String
  def adornUsernameForEjabberd(username:String):String = username
  def adornUsernameForYaws(username:String):String = username
  def vendClientConfiguration(username:String):Option[ClientConfiguration] = {
    for (
      cc <- MeTLXConfiguration.clientConfig;
      (xu,xp,hu,hp) <- getPasswords(username)
    ) yield {
      cc.copy(
        xmppUsername = xu,
        xmppPassword = xp
      )
    }
  }
}
class StableKeyConfigurationProvider extends ConfigurationProvider {
  protected def generatePasswordForEjabberd(username:String):String = nextFuncName
  protected def generatePasswordForYaws(username:String):String = nextFuncName
}

class StableKeyWithRemoteCheckerConfigurationProvider(scheme:String,localPort:Int,remoteBackendHost:String,remoteBackendPort:Int) extends ConfigurationProvider {
  protected val ejPassword = nextFuncName
  protected val verifyPath:String = "verifyUserCredentials"
  protected val returnAddress:String = {
    "%s:%s".format(getLocalIp,getLocalPort)
  }
  protected val getLocalIp:String = {
    val socket = new java.net.Socket(remoteBackendHost,remoteBackendPort)
    val ip = socket.getLocalAddress.toString match {
      case s if s.startsWith("/") => s.drop(1)
      case s => s
    }
    socket.close()
    ip
  }
  protected val getLocalPort:String = {
    localPort.toString
  }
  override def checkPassword(username:String,password:String):Boolean = {
    if (username.startsWith("ejUserAndIp_"))
      password == ejPassword
    else
      super.checkPassword(username,password)
  }
  protected def generatePasswordForEjabberd(username:String):String = nextFuncName
  protected def generatePasswordForYaws(username:String):String = nextFuncName
  override def adornUsernameForEjabberd(username:String):String = "ejUserAndIp|%s|%s".format(username,scheme,getLocalIp,getLocalPort,verifyPath)
  override def adornUsernameForYaws(username:String):String = "%s@%s".format(username,returnAddress)
}

class StaticKeyConfigurationProvider(ejabberdUsername:Option[String],ejabberdPassword:String,yawsUsername:Option[String],yawsPassword:String) extends ConfigurationProvider {
  ejabberdUsername.foreach(eu => {
    keys.update(eu,ejabberdPassword)
  })
  yawsUsername.foreach(yu => {
    keys.update(yu,yawsPassword)
  })
  override def checkPassword(username:String,password:String):Boolean = {
    debug("checking: %s %s in %s".format(username,password,keys))
    ejabberdUsername.filter(_ == username).map(_u => password == ejabberdPassword).getOrElse(false) ||
    yawsUsername.filter(_ == username).map(_u => password == yawsPassword).getOrElse(false) ||
    keys.get(username).exists(_ == password)
  }
  protected def generatePasswordForEjabberd(username:String):String = ejabberdPassword
  protected def generatePasswordForYaws(username:String):String = yawsPassword
  override def adornUsernameForEjabberd(username:String):String = ejabberdUsername.getOrElse(username)
  override def adornUsernameForYaws(username:String):String = yawsUsername.getOrElse(username)
}
