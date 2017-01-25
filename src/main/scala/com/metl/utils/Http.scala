package com.metl.utils

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.cert._
import java.util.concurrent._
import java.util.{ArrayList, Date}
import javax.net.ssl._

import net.liftweb.common.Logger
import net.liftweb.util._
import org.apache.commons.io.IOUtils
import org.apache.http._
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods._
import org.apache.http.conn._
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.conn.scheme._
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.{ByteArrayEntity, ContentType, StringEntity}
import org.apache.http.impl.client._
import org.apache.http.impl.conn.tsccm._
import org.apache.http.message.{BasicHttpEntityEnclosingRequest, BasicHttpRequest, BasicNameValuePair, _}
import org.apache.http.protocol.HTTP
import org.apache.http.util._

case class RedirectException(message:String,exceptions:List[Throwable] = List.empty[Throwable]) extends Exception(message){}
case class RetryException(message:String,exceptions:List[Throwable] = List.empty[Throwable]) extends Exception(message){}
case class WebException(message:String,code:Int,path:String) extends Exception(message){}

trait IMeTLHttpClient {
  def addAuthorization(domain:String,username:String,password:String):Unit

  def get(uri:String):String = get(uri,List.empty[(String,String)])
  def get(uri:String,additionalHeaders:List[(String,String)]):String
  def getExpectingHTTPResponse(uri:String,additionalHeaders:List[(String,String)] = List.empty[(String,String)],retriesSoFar:Int = 0, redirectsSoFar:Int = 0,exceptions:List[Throwable] = List.empty[Throwable],startTime:Long = new Date().getTime):HTTPResponse

  def getAsString(uri:String):String = getAsString(uri,List.empty[(String,String)])
  def getAsString(uri:String,additionalHeaders:List[(String,String)]):String

  def getAsBytes(uri:String):Array[Byte] = getAsBytes(uri, List.empty[(String,String)])
  def getAsBytes(uri:String,additionalHeaders:List[(String,String)]):Array[Byte]

  def putBytes(uri:String,bytes:Array[Byte]): Array[Byte] = putBytes(uri, bytes, List.empty[(String,String)])
  def putBytes(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)]): Array[Byte]
  def putBytesExpectingHTTPResponse(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)]):HTTPResponse

  def putString(uri:String,data:String): String = putString(uri, data, List.empty[(String,String)])
  def putString(uri:String,data:String,additionalHeaders:List[(String,String)]): String
  def putStringExpectingHTTPResponse(uri:String,data:String,additionalHeaders:List[(String,String)]):HTTPResponse

  def postBytes(uri:String,bytes:Array[Byte]): Array[Byte] = postBytes(uri, bytes, List.empty[(String,String)])
  def postBytes(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)]):Array[Byte]
  def postBytesExpectingHTTPResponse(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)]):HTTPResponse

  def postForm(uri:String,postItemList:List[(String,String)]):Array[Byte] = postForm(uri, postItemList, List.empty[(String,String)])
  def postForm(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)]):Array[Byte]
  def postFormExpectingHTTPResponse(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)]):HTTPResponse


  def postMultipart(uri:String,binaryParts:List[(String,Array[Byte])],stringParts:List[(String,String)],additionalHeaders:List[(String,String)]):Array[Byte]

  def postUnencodedForm(uri:String,postItemList:List[(String,String)]):Array[Byte] = postUnencodedForm(uri, postItemList, List.empty[(String,String)])
  def postUnencodedForm(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)]):Array[Byte]
  def postUnencodedFormExpectingHttpResponse(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)]):HTTPResponse

  def setCookies(cookies: Map[String,Header]): Unit
  def getCookies: Map[String,Header]

  def setHttpHeaders(headers:List[Header]): Unit
  def getHttpHeaders: List[Header]
}

case class HTTPResponse(requestUrl:String,actOnConn:(ManagedClientConnection,String,String) => Unit,bytes:Array[Byte],statusCode:Int,headers:Map[String,String],startMilis:Long,endMilis:Long,numberOfRetries:Int = 0, numberOfRedirects:Int = 0,exceptions:List[Throwable] = List.empty[Throwable]){
  val duration = endMilis - startMilis
  def responseAsString = new String(bytes)
}

class CleanHttpClient(connMgr:ClientConnectionManager) extends DefaultHttpClient(connMgr) with IMeTLHttpClient with Logger {
  protected val connectionTimeout = 120
  //protected val connectionTimeout = 30
  protected val keepAliveTimeout = 120
  protected val readTimeout = 240000
  protected def httpParams = getParams
  protected def httpContext = createHttpContext
  protected val maxRedirects = 20
  protected val maxRetries = 2
  protected val keepAliveEnabled = true
  private var authorizations:Map[String,(String,String)] = Map.empty[String,(String,String)].withDefault((location) => ("anonymous","unauthorized"))
  protected var cookies = Map.empty[String,Header]
  protected var httpHeaders = {
    keepAliveEnabled match {
      case true => Array[Header](new BasicHeader("Connection","keep-alive"))
      case false => Array.empty[Header]
    }
  }

  override def setCookies(cook:Map[String,Header]):Unit = cookies = cook
  override def getCookies : Map[String, Header] = cookies

  def addHttpHeader(name:String,value:String):Unit = setHttpHeaders(getHttpHeaders ::: List(new BasicHeader(name,value)))
  override def setHttpHeaders(headers:List[Header]):Unit = httpHeaders = headers.toArray
  override def getHttpHeaders : List[Header] = httpHeaders.toList

  protected def addAdditionalHeaders(message:AbstractHttpMessage,additionalHeaders:List[(String,String)]):Unit = {
    if (additionalHeaders.length > 0)
      additionalHeaders.foreach(header => message.addHeader(new BasicHeader(header._1,header._2)))
  }
  override def addAuthorization(domain:String,username:String,password:String):Unit = {
    authorizations = authorizations.updated(domain,(username,password))
  }
  protected def applyDefaultHeaders(message:AbstractHttpMessage, uri:URI):Unit = {
    httpHeaders.foreach(message.addHeader)
    applyHostHeader(message,uri)
    applyClientCookies(message,uri)
    applyAuthHeader(message,uri)
  }
  protected def applyAuthHeader(message:AbstractHttpMessage,uri:URI):Unit = {
    val host = uri.toString
    val constructedHost = determineScheme(uri)+"://"+determineHost(uri)+":"+determinePort(uri)+"/"+determinePath(uri)
    authorizations.map(auth => auth match {
      case (domain,("anonymous","unauthorized")) => {}
      case (domain,(username,password)) if (host.contains(domain) || constructedHost.contains(domain) || domain == "" || domain == "*") => {
        val authorizationString = SecurityHelpers.base64Encode(("%s:%s".format(username,password)).getBytes("UTF-8"))
        message.addHeader(new BasicHeader("Authorization","Basic %s".format(authorizationString)))
      }
    })
  }
  protected def applyHostHeader(message:AbstractHttpMessage,uri:URI):Unit = {
    val port = determinePort(uri)
    val host = determineHost(uri)
    val blacklist = List(80,443)
    if (blacklist.contains(port))
      message.addHeader(new BasicHeader("Host",host))
    else
      message.addHeader(new BasicHeader("Host","%s:%s".format(host,port)))
  }
  protected def withConn(uri:String,actOnConn:(ManagedClientConnection,String,String) => Unit,redirectNumber:Int = 0,retryNumber:Int = 0, exceptionsSoFar:List[Throwable] = List.empty[Throwable],start:Long = new Date().getTime):HTTPResponse = Stopwatch.time("Http.withConn", {
    try {
      if ((maxRedirects > 0) && (redirectNumber > maxRedirects || exceptionsSoFar.filter(e => e.isInstanceOf[RedirectException]).length > maxRedirects)) {
        throw new RedirectException("exceeded configured maximum number of redirects (%s) when requesting: %s".format(maxRedirects,uri),exceptionsSoFar)
      }
      if ((maxRetries > 0) && (retryNumber > maxRetries)){
        throw new RetryException("exceed maximum number of retries (%s) when requesting: %s".format(maxRetries,uri),exceptionsSoFar)
      }
      val correctlyFormedUrl = new URI(uri)
      val port = determinePort(correctlyFormedUrl)
      val host = determineHost(correctlyFormedUrl)
      val path = determinePath(correctlyFormedUrl)
      val scheme = determineScheme(correctlyFormedUrl)
      val query = determineQuery(correctlyFormedUrl)
      val route = new HttpRoute(new HttpHost(host,port,scheme))
      val connRequest = connMgr.requestConnection(route,null)
      val conn = connRequest.getConnection(connectionTimeout,TimeUnit.SECONDS)
      try {
        conn.open(route,httpContext,httpParams)
        val relPath = path+query
        actOnConn(conn,uri,relPath)
        if(conn.isResponseAvailable(readTimeout)){
          val response = conn.receiveResponseHeader
          storeClientCookies(response.getHeaders("Set-Cookie").toList)
          conn.receiveResponseEntity(response)
          val entity = response.getEntity
          val tempOutput = IOUtils.toByteArray(entity.getContent)
          val end = new Date().getTime
          val responseOutput = HTTPResponse(uri,actOnConn,tempOutput,response.getStatusLine.getStatusCode,Map(response.getAllHeaders.toList.map(h => (h.getName,h.getValue)):_*),start,end,retryNumber,redirectNumber,exceptionsSoFar)
          EntityUtils.consume(entity)
          connMgr.releaseConnection(conn,keepAliveTimeout,TimeUnit.SECONDS)
          responseOutput
        } else {
          connMgr.releaseConnection(conn,keepAliveTimeout,TimeUnit.SECONDS)
          throw new Exception("Socket timeout - No data from: %s".format(uri))
        }
      } catch {
        case ex:Throwable => {
          conn.abortConnection
          executeHttpConnAction(uri,actOnConn,retryNumber + 1, redirectNumber, exceptionsSoFar ::: List(ex),start)
        }
      }
    } catch {
      case ex:RetryException => throw new RetryException(ex.getMessage,ex.exceptions)
      case ex:RedirectException => throw new RedirectException(ex.getMessage,ex.exceptions)
      case ex:Throwable => throw ex
    }
  })
  protected def displayMetrics(conn:ManagedClientConnection):Unit = {
    val m = conn.getMetrics
    debug("sent: %s (%s bytes)".format(m.getRequestCount,m.getSentBytesCount))

    debug("rec'd:     %s (%s bytes)".format(m.getResponseCount,m.getReceivedBytesCount))
  }

  override def putString(uri:String,data:String,additionalHeaders:List[(String,String)]): String = {
    respondToResponse(putStringExpectingHTTPResponse(uri,data,additionalHeaders),additionalHeaders).responseAsString
  }
  override def putStringExpectingHTTPResponse(uri:String,data:String,additionalHeaders:List[(String,String)]):HTTPResponse = {
    val stringPuttingPut = (conn:ManagedClientConnection,url:String,path:String) => {
      val correctlyFormedUrl = new URI(url)
      val putMethod = new BasicHttpEntityEnclosingRequest("PUT",path){override val expectContinue = false}
      val putEntity = new StringEntity(data,"UTF-8")
      applyDefaultHeaders(putMethod,correctlyFormedUrl)
      addAdditionalHeaders(putMethod,additionalHeaders)
      putMethod.setEntity(putEntity)
      putMethod.addHeader(new BasicHeader("Content-Length",putEntity.getContentLength.toString))
      conn.sendRequestHeader(putMethod)
      conn.sendRequestEntity(putMethod)
      conn.flush
    }
    executeHttpConnAction(uri,stringPuttingPut)
  }
  override def putBytes(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):Array[Byte] = Stopwatch.time("Http.putBytes", {
    respondToResponse(putBytesExpectingHTTPResponse(uri,bytes,additionalHeaders),additionalHeaders).bytes
  })
  override def putBytesExpectingHTTPResponse(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):HTTPResponse = Stopwatch.time("Http.putBytesExpectingHTTPResponse", {
    val bytePostingPut = (conn:ManagedClientConnection,url:String,path:String) => {
      val correctlyFormedUrl = new URI(url)
      val putMethod = new BasicHttpEntityEnclosingRequest("PUT",path){override val expectContinue = false}
      val putEntity = new ByteArrayEntity(bytes)
      applyDefaultHeaders(putMethod,correctlyFormedUrl)
      addAdditionalHeaders(putMethod,additionalHeaders)
      putMethod.setEntity(putEntity)
      putMethod.addHeader(new BasicHeader("Content-Length",putEntity.getContentLength.toString))
      conn.sendRequestHeader(putMethod)
      conn.sendRequestEntity(putMethod)
      conn.flush
    }
    executeHttpConnAction(uri,bytePostingPut)
  })

  override def postBytes(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):Array[Byte] = Stopwatch.time("Http.postBytes", {
    respondToResponse(postBytesExpectingHTTPResponse(uri,bytes,additionalHeaders),additionalHeaders).bytes
  })
  override def postBytesExpectingHTTPResponse(uri:String,bytes:Array[Byte],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):HTTPResponse = Stopwatch.time("Http.postBytesExpectingHTTPResponse", {
    val bytePostingPost = (conn:ManagedClientConnection,url:String,path:String) => {
      val correctlyFormedUrl = new URI(url)
      val postMethod = new BasicHttpEntityEnclosingRequest("POST",path){override val expectContinue = false}
      val postEntity = new ByteArrayEntity(bytes)
      applyDefaultHeaders(postMethod,correctlyFormedUrl)
      addAdditionalHeaders(postMethod,additionalHeaders)
      postMethod.setEntity(postEntity)
      postMethod.addHeader(new BasicHeader("Content-Length",postEntity.getContentLength.toString))
      conn.sendRequestHeader(postMethod)
      conn.sendRequestEntity(postMethod)
      conn.flush
    }
    executeHttpConnAction(uri,bytePostingPost)
  })
  override def postForm(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):Array[Byte] = Stopwatch.time("Http.postForm", {
    respondToResponse(postFormExpectingHTTPResponse(uri,postItemList,additionalHeaders),additionalHeaders).bytes
  })
  override def postFormExpectingHTTPResponse(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):HTTPResponse = Stopwatch.time("Http.postFormExpectingHTTPResponse", {
    val formPostingPost = (conn:ManagedClientConnection,url:String,path:String) => {
      val correctlyFormedUrl = new URI(url)
      val postMethod = new BasicHttpEntityEnclosingRequest("POST",path){override val expectContinue = false}
      val postForm = new ArrayList[NameValuePair]
      for (postItem <- postItemList){
        postForm.add(new BasicNameValuePair(postItem._1,postItem._2))
      }
      val postEntity = new UrlEncodedFormEntity(postForm,StandardCharsets.UTF_8)
      applyDefaultHeaders(postMethod,correctlyFormedUrl)
      addAdditionalHeaders(postMethod,additionalHeaders)
      postMethod.addHeader(new BasicHeader("Content-Type","""application/x-www-form-urlencoded"""))
      postMethod.addHeader(new BasicHeader("Content-Length",postEntity.getContentLength.toString))
      postMethod.setEntity(postEntity)
      conn.sendRequestHeader(postMethod)
      conn.sendRequestEntity(postMethod)
      conn.flush
    }
    executeHttpConnAction(uri,formPostingPost)
  })
  override def postUnencodedForm(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):Array[Byte] = Stopwatch.time("Http.postUnencodedForm", {
    respondToResponse(postUnencodedFormExpectingHttpResponse(uri,postItemList,additionalHeaders),additionalHeaders).bytes
  })
  override def postUnencodedFormExpectingHttpResponse(uri:String,postItemList:List[(String,String)],additionalHeaders:List[(String,String)] = List.empty[(String,String)]):HTTPResponse = Stopwatch.time("Http.postUnencodedFormExpectingHTTPResponse", {
    val unencodedFormPostingPost = (conn:ManagedClientConnection,url:String,path:String) => {
      val correctlyFormedUrl = new URI(url)
      val postMethod = new BasicHttpEntityEnclosingRequest("POST",path){override val expectContinue = false}
      val postForm = postItemList.map(postItem => postItem._1 +"="+postItem._2).mkString("&")
      val postEntity = new StringEntity(postForm,StandardCharsets.UTF_8)
      applyDefaultHeaders(postMethod,correctlyFormedUrl)
      addAdditionalHeaders(postMethod,additionalHeaders)
      postMethod.addHeader(new BasicHeader("Content-Type","""application/x-www-form-urlencoded"""))
      postMethod.addHeader(new BasicHeader("Content-Length",postEntity.getContentLength.toString))
      postMethod.setEntity(postEntity)
      conn.sendRequestHeader(postMethod)
      conn.sendRequestEntity(postMethod)
      conn.flush
    }
    executeHttpConnAction(uri,unencodedFormPostingPost)
  })

  override def postMultipart(uri:String,binaryParts:List[(String,Array[Byte])],stringParts:List[(String,String)],additionalHeaders:List[(String,String)]):Array[Byte] = {
    val entitySpec = stringParts.foldLeft(
      binaryParts.foldLeft(MultipartEntityBuilder.create){
        case (builder,(name,bytes)) => builder.addBinaryBody(name,bytes,ContentType.create("image/jpeg","utf-8"),name)
      }){
      case (builder,(name,string)) => builder.addTextBody(name,string,ContentType.TEXT_HTML)
    }
    val uploadFile = new HttpPost(uri)
    addAdditionalHeaders(uploadFile,additionalHeaders)
    uploadFile.setEntity(entitySpec.build)
    val response = execute(uploadFile)
    val entity = response.getEntity
    IOUtils.toByteArray(entity.getContent)
  }

  override def get(uri:String,additionalHeaders:List[(String,String)] = List.empty[(String,String)]):String = Stopwatch.time("Http.get", {
    getAsString(uri,additionalHeaders)
  })
  override def getAsString(uri:String,additionalHeaders:List[(String,String)] = List.empty[(String,String)]):String = Stopwatch.time("Http.getAsString", {
    new String(getAsBytes(uri,additionalHeaders))
  })

  override def getAsBytes(uri:String,additionalHeaders:List[(String,String)] = List.empty[(String,String)]):Array[Byte] = Stopwatch.time("Http.getAsBytes", {
    respondToResponse(getExpectingHTTPResponse(uri,additionalHeaders),additionalHeaders).bytes
  })
  override def getExpectingHTTPResponse(uri:String,additionalHeaders:List[(String,String)] = List.empty[(String,String)],retriesSoFar:Int = 0, redirectsSoFar:Int = 0,exceptions:List[Throwable] = List.empty[Throwable],startTime:Long = new Date().getTime):HTTPResponse = Stopwatch.time("Http.getExpectingHTTPResponse", {
    val bytesGettingGet = (conn:ManagedClientConnection,url:String,path:String) => {
      val getMethod = new BasicHttpRequest("GET",path)
      val correctlyFormedUrl = new URI(url)
      applyDefaultHeaders(getMethod,correctlyFormedUrl)
      addAdditionalHeaders(getMethod,additionalHeaders)
      conn.sendRequestHeader(getMethod)
      conn.flush
    }
    executeHttpConnAction(uri,bytesGettingGet,retriesSoFar,redirectsSoFar,exceptions,startTime)
  })

  protected def executeHttpConnAction(uri:String,actOnConn:(ManagedClientConnection,String,String) => Unit,retriesSoFar:Int = 0, redirectsSoFar:Int = 0,exceptions:List[Throwable] = List.empty[Throwable],startTime:Long = new Date().getTime):HTTPResponse = {
    withConn(uri,actOnConn,redirectsSoFar,retriesSoFar,exceptions,startTime)
  }

  protected def determinePort(uri:URI):Int = {
    uri.getPort match {
      case other:Int if (other == -1) => uri.getScheme match {
        case "http" => 80
        case "https" => 443
        case other:String => 80
        case _ => 80
      }
      case other:Int => other
    }
  }
  protected def determineHost(uri:URI):String = {
    uri.getHost match {
      case null => throw new IllegalArgumentException("No hostname supplied")
      case host:String => host
    }
  }
  protected def determinePath(uri:URI):String = {
    val finalPath = uri.getPath match {
      case "" => "/"
      case path:String => path
      case _ => "/"
    }
    "/"+finalPath.dropWhile(c => c == '/')
  }
  protected def determineScheme(uri:URI):String = {
    uri.getScheme match {
      case null => "http"
      case other:String => other
    }
  }
  protected def determineConnSecurity(uri:URI):Boolean = {
    uri.getScheme match {
      case "https" => true
      case _ => false
    }
  }
  protected def determineQuery(uri:URI):String = {
    val finalQuery = uri.getRawQuery match {
      case "" => ""
      case null => ""
      case other:String => "?"+other
    }
    finalQuery.dropWhile(c => c == '/')
  }
  def respondToResponse(response:HTTPResponse,additionalHeaders:List[(String,String)] = List.empty[(String,String)]):HTTPResponse = {
    val uri = response.requestUrl
    val tempOutput = response.bytes
    response.statusCode match {
      case 200 | 201 => response
      case 300 | 301 | 302 | 303 => {
        val newLoc = response.headers("Location")
        val newLocUri = new URI(newLoc)
        val oldLoc = new URI(uri)
        val newLocString = if (newLocUri.getHost == null) {
          oldLoc.resolve(newLocUri).toString
        } else {
          newLoc
        }
        respondToResponse(getExpectingHTTPResponse(newLocString,additionalHeaders,response.numberOfRetries,response.numberOfRedirects + 1,response.exceptions ::: List(new RedirectException("healthy redirect from %s to %s".format(uri,newLocString),response.exceptions)),response.startMilis))
      }
      case 307 => {
        val newLoc = response.headers("Location")
        val newLocUri = new URI(newLoc)
        val oldLoc = new URI(uri)
        val newLocString = if (newLocUri.getHost == null) {
          oldLoc.resolve(newLocUri).toString
        } else {
          newLoc
        }
        respondToResponse(executeHttpConnAction(newLocString,response.actOnConn,response.numberOfRetries,response.numberOfRedirects + 1,response.exceptions ::: List(new RedirectException("healthy redirect from %s to %s".format(uri,newLocString),response.exceptions)),response.startMilis))
      }
      case 400 => throw new WebException("bad request sent to %s: %s".format(uri,tempOutput),400,uri)
      case 401 => throw new WebException("access to object at %s requires authentication".format(uri),401,uri)
      case 403 => throw new WebException("access forbidden to object at %s".format(uri),403,uri)
      case 404 => throw new WebException("object not found at %s".format(uri),404,uri)
      case 500 => throw new WebException("server error encountered at %s: %s".format(uri,response.responseAsString),500,uri)
      case other => throw new WebException("http status code (%s) not yet implemented, returned from %s".format(other,uri),other,uri)
    }
  }
  protected def applyClientCookies(request:AbstractHttpMessage,uri:URI) = {
    val cookieString = cookies.map(cookieList => {
      val cookiesContained = cookieList._2.getElements.toList
      cookiesContained.map(cookie =>{
        val cookieName = cookie.getName
        val cookieValue = cookie.getValue
        val cookieParams = cookie.getParameters
        val cookieDomain = cookieParams.find(p => p.getName.toLowerCase == "domain") match {
          case Some(dom) => dom.getValue
          case None => "/"
        }
        val testTrue = uri.toString.toLowerCase.contains(cookieDomain.toLowerCase)
        if (testTrue)
          "%s=%s".format(cookieName,cookieValue)
        else ""
      })
    }).flatten.mkString("; ").trim.dropWhile(c => c == ';').reverse.dropWhile(c => c == ';').reverse.trim
    request.addHeader(new BasicHeader("Cookie",cookieString))
  }
  protected def storeClientCookies(newHeaders:List[Header]):Unit = {
    val newCookies = newHeaders.map(header => header.getElements.toList.map(item => (item.getName, header)))
    newCookies.foreach(newCookieList => newCookieList.foreach(newCookie => cookies = cookies.updated(newCookie._1,newCookie._2)))
  }
}

object HttpClientProviderConfigurator {
  import scala.xml._
  def configureFromXml(in:NodeSeq):HttpClientProvider = {
    val nodes = (in \\ "httpClientConfiguration").headOption.getOrElse(NodeSeq.Empty)
    val connectionTimeout = (nodes \\ "@connectionTimeout").headOption.map(_.text.toInt).getOrElse(3600)
    val keepAliveTimeout = (nodes \\ "@keepAliveTimeout").headOption.map(_.text.toInt).getOrElse(5400)
    val readTimeout = (nodes \\ "@readTimeout").headOption.map(_.text.toInt).getOrElse(7200000)
    val maxRedirects = (nodes \\ "@maxRedirects").headOption.map(_.text.toInt).getOrElse(20)
    val maxRetries = (nodes \\ "@maxRetries").headOption.map(_.text.toInt).getOrElse(2)
    val keepAliveEnabled = (nodes \\ "@keepAliveEnabled").headOption.map(_.text.toBoolean).getOrElse(true)
    val maxConnectionsPerRoute = (nodes \\ "@maxConnectionsPerRoute").headOption.map(_.text.toInt).getOrElse(500)
    val maxConnectionsTotal = (nodes \\ "@maxConnectionsTotal").headOption.map(_.text.toInt).getOrElse(1000)
    val trustAllHosts = (nodes \\ "@trustAllHosts").headOption.map(_.text.toBoolean).getOrElse(true)
    new HttpClientProvider(connectionTimeout,keepAliveTimeout,readTimeout,maxRedirects,maxRetries,keepAliveEnabled,maxConnectionsPerRoute,maxConnectionsTotal,trustAllHosts) 
  }
}

class HttpClientProvider(
  connectionTimeout:Int = 3600,
  keepAliveTimeout:Int = 5400,
  readTimeout:Int = 7200000,
  maxRedirects:Int = 20,
  maxRetries:Int = 2,
  keepAliveEnabled:Boolean = true,
  maxConnectionsPerRoute:Int = 500,
  maxConnectionsTotal:Int = 1000,
  trustAllHosts:Boolean = true
){
  private object TrustAllHosts extends HostnameVerifier{
    override def verify(_host:String,_session:SSLSession)= true
  }
  private object TrustingTrustManager extends X509TrustManager{
    override def getAcceptedIssuers = Array.empty[X509Certificate]
    override def checkClientTrusted(certs:Array[X509Certificate], t:String) = ()
    override def checkServerTrusted(certs:Array[X509Certificate], t:String) = ()
  }
  
  private val getSchemeRegistry = {
    val ssl_ctx = SSLContext.getInstance("TLS")
    val managers = Array[TrustManager](TrustingTrustManager)
    ssl_ctx.init(null, managers, new java.security.SecureRandom())
    val sslSf = new org.apache.http.conn.ssl.SSLSocketFactory(ssl_ctx, org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
    val schemeRegistry = new SchemeRegistry()
    schemeRegistry.register(new Scheme("https", 443, sslSf))
    schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory,80))
    schemeRegistry
  }
  protected lazy val trustingDefaultClient = new DefaultHttpClient()
  def getClient = Stopwatch.time("Http.getClient", {
    val ct = connectionTimeout
    val kat = keepAliveTimeout
    val rt = readTimeout
    val maxRed = maxRedirects
    val maxRet = maxRetries
    val kae = keepAliveEnabled
    val connMgr = trustAllHosts match {
      case true => {
        val connMgr = new ThreadSafeClientConnManager(getSchemeRegistry)
        connMgr.setDefaultMaxPerRoute(maxConnectionsPerRoute)
        connMgr.setMaxTotal(maxConnectionsTotal)
        connMgr
      }
      case false => {
        val mgr = trustingDefaultClient.getConnectionManager()
        //val params = trustingDefaultClient.getParams()
        //val connMgr = new ThreadSafeClientConnManager(params,mgr.getSchemeRegistry())
        val connMgr = new ThreadSafeClientConnManager(mgr.getSchemeRegistry())
        connMgr.setDefaultMaxPerRoute(maxConnectionsPerRoute)
        connMgr.setMaxTotal(maxConnectionsTotal)
        connMgr
      }
    }
    new CleanHttpClient(connMgr){
      override val connectionTimeout = ct
      override val keepAliveTimeout = kat
      override val readTimeout = rt
      override val maxRedirects = maxRed
      override val maxRetries = maxRet
      override val keepAliveEnabled = kae
    }
  })
  def getAuthedClient(username:String,password:String,domain:String = "*") = Stopwatch.time("Http.getAuthedClient", {
    val client = getClient
    client.addAuthorization(domain,username,password)
    client
  })
  def cloneClient(incoming:CleanHttpClient):CleanHttpClient = Stopwatch.time("Http.cloneClient", {
    val client = getClient
    client.setCookies(incoming.getCookies)
    client.setHttpHeaders(incoming.getHttpHeaders)
    client
  })
  def getClient(headers:List[(String,String)]):CleanHttpClient = Stopwatch.time("Http.getClient(headers)", {
    val newHeaders = headers.map(tup => new BasicHeader(tup._1,tup._2)).toList
    val client = getClient
    client.setHttpHeaders(newHeaders)
    client
  })
}
