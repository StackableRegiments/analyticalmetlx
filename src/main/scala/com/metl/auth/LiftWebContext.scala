package monash.SAML

import java.util

import net.liftweb.common.Logger
import net.liftweb.http.{CurrentReq, SessionVar}
import org.pac4j.core.context.WebContext

class NotDefinedError(msg:String) extends Error

class LiftWebContext(scheme:String, serverName:String, port:Int = 80) extends WebContext with ProxyTrace with Logger {

  object sessionSAML extends SessionVar[scala.collection.immutable.Map[String, AnyRef]](scala.collection.immutable.Map.empty[String, AnyRef])

  override def setResponseHeader(name: String, value: String) { /* Do nothing */ }

  override def setResponseStatus(code: Int) = { /* Do nothing */ }

  override def getRequestParameters: util.Map[String, Array[String]] = throw new NotDefinedError("getRequestParameters not implemented")

  override def getRequestHeader(name: String): String = throw new NotDefinedError("getRequestHeader not implemented")

  override def writeResponseContent(content: String) = throw new NotDefinedError("writeResponseContent not implemented")

  //  override def getServerName: String = getProxyServerName(CurrentReq.value)

  override def getServerName: String = serverName

  override def getRequestParameter(name: String): String = CurrentReq.value.param(name).orNull

  override def getRequestMethod: String = CurrentReq.value.request.method

  override def setSessionAttribute(name: String, value: scala.AnyRef): Unit = sessionSAML.set(sessionSAML.get.updated(name,value))

  //  override def getServerPort: Int = getProxyPort(CurrentReq.value)

  override def getServerPort: Int = port

  override def getSessionAttribute(name: String): AnyRef = sessionSAML.get.getOrElse(name, null)

  //  override def getScheme: String = getProxyScheme(CurrentReq.value)

  override def getScheme: String = scheme

  override def getFullRequestURL: String = {

    val FullRequestURL = if ( port == 80 ) {
      "%s://%s/%s".format(getScheme, getServerName, CurrentReq.value.path.wholePath.mkString("/"))
    } else {
      "%s://%s:%s/%s".format(getScheme, getServerName, getServerPort, CurrentReq.value.path.wholePath.mkString("/"))
    }

    FullRequestURL
  }
}
