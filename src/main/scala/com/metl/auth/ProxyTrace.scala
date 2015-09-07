package monash.SAML

import net.liftweb.common.{Logger, Empty, Full}
import net.liftweb.http.Req
import net.liftweb.util.ControlHelpers._

trait ProxyTrace extends Logger
{
  private def getLastReverseProxyHeaderValue(request:Req, name:String, defaultValue:String) = {
    request.header(name) match { //find out whether the originally requested URL is the first item in the X-Forwarded-For list, or the last.
      case Full(str) if !str.isEmpty =>
        debug("getLastReverseProxyHeaderValue: %s".format(str))
        str.split(",").headOption.getOrElse(defaultValue)
      case Full(str) if str.isEmpty => defaultValue
      case Empty => defaultValue
      case _ => defaultValue
    }
  }

  def getOriginalRemoteAddr(request: Req):String = {
    val remoteAddr = request.remoteAddr

    debug("getOriginalRemoteAddr[remoteAddr]: %s".format(remoteAddr))

    val value = getLastReverseProxyHeaderValue(request,"X-Forwarded-For", remoteAddr)

    debug("getOriginalRemoteAddr[value]: %s".format(value))

    value
  }

  def getProxyServerName(request:Req) = {
    val optionOfProxyHostName = getProxyHost(request).split(":").headOption

    val hostName = optionOfProxyHostName.getOrElse(request.hostName)

    debug("getProxyServerName[hostName]: %s".format(hostName))

    val value = getLastReverseProxyHeaderValue(request,"X-Forwarded-Server", hostName)

    debug("getProxyServerName[value]: %s".format(value))

    value
  }

  def getProxyScheme(request:Req) = {
    val scheme = request.request.scheme

    debug("getProxyScheme[scheme]: %s".format(scheme))

    val value = getLastReverseProxyHeaderValue(request,"X-Forwarded-Proto", scheme)

    debug("getProxyScheme[value]: %s".format(value))

    value
  }

  def getProxyHost(request:Req) = {
    val host = request.header("Host").getOrElse("unknown")

    debug("getProxyHost[scheme]: %s".format(host))

    val value = getLastReverseProxyHeaderValue(request,"X-Forwarded-Host", host)

    debug("getProxyHost[value]: %s".format(value))

    value
  }

  def getProxyPort(request:Req) = {
    val port = request.request.serverPort

    debug("getProxyPort[port]: %s".format(port))

    val host = getProxyHost(request)

    val value = host.split(":").lastOption match {
      case Some(str) =>
        toInt(str) match {
          case Full(proxyPort:Int) => proxyPort
          case _ => 80
        }
      case None => port
    }

    debug("getProxyPort[value]: %s".format(value))

    value
  }

  private def toInt(s: String) = {
    tryo { s.toInt }
  }
}
