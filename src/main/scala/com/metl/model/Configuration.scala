package com.metl.model

import net.liftweb.http._
import net.liftweb.http.SHtml._
import net.liftweb.common._
import S._
import net.liftweb.util._
import Helpers._
import scala.xml._
import scala.collection.mutable.{Map=>MutableMap}

case class ClientConfiguration(xmppHost:String,xmppPort:Int,xmppDomain:String,xmppUsername:String,xmppPassword:String,conversationSearchUrl:String,webAuthenticationUrl:String,thumbnailUrl:String,resourceUrl:String,historyUrl:String,httpUsername:String,httpPassword:String,structureDirectory:String,resourceDirectory:String,uploadPath:String,primaryKeyGenerator:String,cryptoKey:String,cryptoIV:String,imageUrl:String)

abstract class ConfigurationProvider {
  val keys:MutableMap[String,String] = MutableMap.empty[String,String]
  def checkPassword(username:String,password:String):Boolean = {
    println("checking: %s %s in %s".format(username,password,keys))
    keys.get(username).exists(_ == password)
  }
  def getPasswords(username:String):Option[Tuple2[String,String]] = {
    val xu = adornUsernameForEjabberd(username)
    val hu = adornUsernameForYaws(username)

    val xp = keys.get(xu) match {
      case None => {
        val np = generatePasswordForEjabberd(xu)
        keys += (xu,np)
        Some(np)
      }
      case some => some
    }
    val hp = keys.get(hu) match {
      case None => {
        val np = generatePasswordForYaws(hu)
        keys += (hu,np)
        Some(np)
      }
      case some => some
    }
    for (
      x <- xp;
      h <- hp
    ) yield {
      (x,h)
    }
  }
  protected def generatePasswordForYaws(username:String):String 
  protected def generatePasswordForEjabberd(username:String):String 
  def adornUsernameForEjabberd(username:String):String 
  def adornUsernameForYaws(username:String):String 
  def vendClientConfiguration(username:String):Option[ClientConfiguration] = {
    for (
      cc <- MeTLXConfiguration.clientConfig;
      xu = adornUsernameForEjabberd(username);
      hu = adornUsernameForYaws(username);
      (xp,hp) <- getPasswords(username)
    ) yield {
      cc.copy(
        xmppUsername = xu,
        xmppPassword = xp,
        httpUsername = hu,
        httpPassword = hp
      )
    }
  }
}
class StableKeyConfigurationProvider(localPort:Int,remoteBackendHost:String,remoteBackendPort:Int) extends ConfigurationProvider {
  protected val returnAddress:String = {
    "%s:%s".format(getLocalIp,getLocalPort)    
  }
  protected def getLocalIp:String = {
    val socket = new java.net.Socket(remoteBackendHost,remoteBackendPort)
    val ip = socket.getLocalAddress.toString match {
      case s if s.startsWith("/") => s.drop(1)
      case s => s
    }
    socket.close()
    return ip;
  }
  protected def getLocalPort:String = {
    localPort.toString
  }
  protected def generatePasswordForEjabberd(username:String):String = nextFuncName
  protected def generatePasswordForYaws(username:String):String = nextFuncName
  def adornUsernameForEjabberd(username:String):String = "ejUserAndIp_%s_%s".format(username,returnAddress)
  def adornUsernameForYaws(username:String):String = "%s@%s".format(username,returnAddress)
}

class StaticKeyConfigurationProvider(ejabberdUsername:Option[String],ejabberdPassword:String,yawsUsername:Option[String],yawsPassword:String) extends ConfigurationProvider {
  protected def generatePasswordForEjabberd(username:String):String = ejabberdPassword
  protected def generatePasswordForYaws(username:String):String = yawsPassword
  def adornUsernameForEjabberd(username:String):String = ejabberdUsername.getOrElse(username)
  def adornUsernameForYaws(username:String):String = yawsUsername.getOrElse(username)
}
