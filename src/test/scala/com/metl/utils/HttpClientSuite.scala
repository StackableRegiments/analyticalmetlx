package com.metl.utils

import net.liftweb.util._
import net.liftweb.common._

import org.scalatest._
import org.scalatest.fixture
import org.scalatest.fixture.ConfigMapFixture
import org.scalatest.mock.MockitoSugar
import org.scalatest.OptionValues._
import org.scalatest.prop.TableDrivenPropertyChecks._

import org.mockito.Mockito._
import org.mockito.Matchers.{eq => the, any, anyInt}

import java.util.concurrent.TimeUnit
import java.io.IOException
import org.apache.http.{HttpResponse, HttpStatus, HttpVersion, ProtocolVersion, HttpRequest, HttpEntityEnclosingRequest, HttpException}
import org.apache.http.entity.StringEntity
import org.apache.http.conn.{ClientConnectionManager, ManagedClientConnection, ClientConnectionRequest}
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.message.{BasicStatusLine, BasicHeader, BasicHttpResponse}

import com.metl.utils._ 

trait HttpClientHelpers {
    def prepareHttpResponse(expectedBody: String, expectedStatusCode: Int): HttpResponse = {
    
        var response = new BasicHttpResponse(HttpVersion.HTTP_1_1, expectedStatusCode, "OK")
        response.setEntity(new StringEntity(expectedBody))
        response.setStatusCode(expectedStatusCode)
        response
    }
}

class HttpClientSuite extends fixture.FunSuite with ConfigMapFixture with MockitoSugar with HttpClientHelpers {

    case class F(connMgr: ClientConnectionManager, client: CleanHttpClient, conn: ManagedClientConnection, connRequest: ClientConnectionRequest)

    private val mockUri = "http://test.metl.com/data.xml"
    private val additionalHeader = List(("Accept", "text/plain"))

    def withClient(test: F => Any) {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        val fixture = F(connMgr, client, conn, connRequest)
        test(fixture)
    }

    def withConnection(test: F => Any) {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        when(connRequest.getConnection(anyInt, any(classOf[TimeUnit]))).thenReturn(conn)
        when(connMgr.requestConnection(any(classOf[HttpRoute]), any)).thenReturn(connRequest)

        val fixture = F(connMgr, client, conn, connRequest)
        test(fixture)
    }

    def withConnectionAndResponse(test: F => Any) {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        when(connRequest.getConnection(anyInt, any(classOf[TimeUnit]))).thenReturn(conn)
        when(connMgr.requestConnection(any(classOf[HttpRoute]), any)).thenReturn(connRequest)
        when(conn.isResponseAvailable(anyInt)).thenReturn(true)

        val fixture = F(connMgr, client, conn, connRequest)
        test(fixture)
    }

    test("shutdown clean http client") { () => 
        withClient { f =>
        
            f.client.getConnectionManager.shutdown

            verify(f.connMgr).shutdown
        }
    }

    test("get with empty string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.get("")
                assert(result === "")
            }
        }
    }

    test("get as bytes with empty string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsBytes("")
                assert(result === Array.empty[Byte])
            }
        }
    }

    test("get as string with empty string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsString("")
                assert(result === "")
            }
        }
    }

    test("get with empty string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.get("", additionalHeader)
                assert(result === "")
            }
        }
    }

    test("get as bytes with empty string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsBytes("", additionalHeader)
                assert(result === Array.empty[Byte])
            }
        }
    }

    test("get as string with empty string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsString("", additionalHeader)
                assert(result === "")
            }
        }
    }

    test("get with junk string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.get("garbage")
                assert(result === "")
            }
        }
    }

    test("get as bytes with junk string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsBytes("garbage")
                assert(result === Array.empty[Byte])
            }
        }
    }

    test("get as string with junk string as uri") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsString("garbage")
                assert(result === "")
            }
        }
    }

    test("get with junk string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.get("garbage", additionalHeader)
                assert(result === "")
            }
        }
    }

    test("get as bytes with junk string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsBytes("garbage", additionalHeader)
                assert(result === Array.empty[Byte])
            }
        }
    }

    test("get as string with junk string as uri and additional header") { () =>
        withClient { f =>
        
            intercept[IllegalArgumentException] {

                val result = f.client.getAsString("garbage", additionalHeader)
                assert(result === "")
            }
        }
    }

    test("get with socket timeout") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.get(mockUri)
              assert(result === "")
            }
        }
    }

    test("get bytes with socket timeout") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.getAsBytes(mockUri)
              assert(result === Array.empty[Byte])
            }
        }
    }

    test("get string with socket timeout") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.getAsString(mockUri)
              assert(result === "")
            }
        }
    }

    test("get with socket timeout and additional header") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.get(mockUri, additionalHeader)
              assert(result === "")
          }
        }
    }

    test("get bytes with socket timeout and additional header") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.getAsBytes(mockUri, additionalHeader)
              assert(result === Array.empty[Byte])
          }
        }
    }

    test("get string with socket timeout and additional header") { () =>
        withConnection { f =>
        
            when(f.conn.isResponseAvailable(anyInt)).thenReturn(false)
         
            intercept[RetryException] {

              val result = f.client.getAsString(mockUri, additionalHeader)
              assert(result === "")
          }
        }
    }

    test("get with retry when receive response header throws io exception") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new IOException())
         
            intercept[RetryException] {

              val result = f.client.get(mockUri)
              assert(result === "")
          }
      }
    }

    test("get as bytes with retry when receive response header throws io exception") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new IOException())
         
            intercept[RetryException] {

              val result = f.client.getAsBytes(mockUri)
              assert(result === Array.empty[Byte])
          }
      }
    }

    test("get as string with retry when receive response header throws io exception") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new IOException())
         
            intercept[RetryException] {

              val result = f.client.getAsString(mockUri)
              assert(result === "")
          }
      }
    }

    test("get with handle http exception from receiveResponseHeader") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new HttpException())
         
            intercept[RetryException] {

              val result = f.client.get(mockUri)
              assert(result === "")
          }
      }
    }

    test("get as bytes with handle http exception from receiveResponseHeader") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new HttpException())
         
            intercept[RetryException] {

              val result = f.client.getAsBytes(mockUri)
              assert(result === Array.empty[Byte])
          }
      }
    }

    test("get as string with handle http exception from receiveResponseHeader") { () =>
        withConnectionAndResponse { f =>

            when(f.conn.receiveResponseHeader).thenThrow(new HttpException())
         
            intercept[RetryException] {

              val result = f.client.getAsString(mockUri)
              assert(result === "")
          }
      }
    }
}

class HttpClientResponseSuite extends FunSuite with MockitoSugar with HttpClientHelpers { 

    case class F(connMgr: ClientConnectionManager, client: CleanHttpClient, conn: ManagedClientConnection, connRequest: ClientConnectionRequest)

    private val mockUri = "http://test.metl.com/data.xml"
    private val additionalHeader = List(("Accept", "text/plain"))

    abstract class Action
    case object Get extends Action
    case object GetAsBytes extends Action
    case object GetAsString extends Action

    val getFunctions = 
      Table(
        ("action", "expectedResult"),
        (Get, "Whatever"),
        (GetAsBytes, "Whatever".toCharArray.map(_.toByte)),
        (GetAsString, "Whatever")
      )

    def clientWithResponse(body: String, statusCode: Int): F = {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        when(connRequest.getConnection(anyInt, any(classOf[TimeUnit]))).thenReturn(conn)
        when(connMgr.requestConnection(any(classOf[HttpRoute]), any)).thenReturn(connRequest)

        val response = prepareHttpResponse(body, statusCode)
        response.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))

        when(conn.isResponseAvailable(anyInt)).thenReturn(true)
        when(conn.receiveResponseHeader).thenReturn(response)

        F(connMgr, client, conn, connRequest)
    }

    def clientWithCustomResponse(customResponse: (ManagedClientConnection) => Unit): F = {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        when(connRequest.getConnection(anyInt, any(classOf[TimeUnit]))).thenReturn(conn)
        when(connMgr.requestConnection(any(classOf[HttpRoute]), any)).thenReturn(connRequest)

        customResponse(conn)

        F(connMgr, client, conn, connRequest)
    }

    def clientWithResponseNoCookie(body: String, statusCode: Int): F = {

        val connMgr = mock[ClientConnectionManager]
        val client = new CleanHttpClient(connMgr)
        val conn = mock[ManagedClientConnection]
        val connRequest = mock[ClientConnectionRequest]

        when(connRequest.getConnection(anyInt, any(classOf[TimeUnit]))).thenReturn(conn)
        when(connMgr.requestConnection(any(classOf[HttpRoute]), any)).thenReturn(connRequest)

        val response = prepareHttpResponse(body, statusCode)

        when(conn.isResponseAvailable(anyInt)).thenReturn(true)
        when(conn.receiveResponseHeader).thenReturn(response)

        F(connMgr, client, conn, connRequest)
    }

    test("response available and has status code of ok") { 
      forAll (getFunctions) { (action, expectedResult) =>
          val f = clientWithResponse("Whatever", HttpStatus.SC_OK)

          val result = action match {
            case Get => f.client.get(mockUri)
            case GetAsBytes => f.client.getAsBytes(mockUri)
            case GetAsString => f.client.getAsString(mockUri)
          }

          assert(result === expectedResult)
          verify(f.connMgr, times(1)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
      }
    }

    test("response available and has status code of ok with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithResponse("Whatever", HttpStatus.SC_OK)

            val result = action match {
                case Get => f.client.get(mockUri, additionalHeader)
                case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                case GetAsString => f.client.getAsString(mockUri, additionalHeader)
            }

            assert(result === expectedResult)
            verify(f.connMgr, times(1)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("response available but has no cookies") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithResponseNoCookie("Whatever", HttpStatus.SC_OK)

            val result = action match {
                case Get => f.client.get(mockUri)
                case GetAsBytes => f.client.getAsBytes(mockUri)
                case GetAsString => f.client.getAsString(mockUri)
            }

            assert(result === expectedResult)
            verify(f.connMgr, times(1)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("response available but has no cookies with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithResponseNoCookie("Whatever", HttpStatus.SC_OK)

            val result = action match {
                case Get => f.client.get(mockUri, additionalHeader)
                case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                case GetAsString => f.client.getAsString(mockUri, additionalHeader)
            }

            assert(result === expectedResult)
            verify(f.connMgr, times(1)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("handle unimplemented status code by retrying") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithResponse("Whatever", HttpStatus.SC_NO_CONTENT)

            intercept[WebException] {

              val result = action match {
                  case Get => f.client.get(mockUri)
                  case GetAsBytes => f.client.getAsBytes(mockUri)
                  case GetAsString => f.client.getAsString(mockUri)
              }
            }
        }
    }

    test("handle unimplemented status code by retrying with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithResponse("Whatever", HttpStatus.SC_NO_CONTENT)

            intercept[WebException] {

              val result = action match {
                case Get => f.client.get(mockUri, additionalHeader)
                case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                case GetAsString => f.client.getAsString(mockUri, additionalHeader)
              }
            }
        }
    }

    test("handle redirect to invalid uri") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) => 
            {
              val response = prepareHttpResponse("Redirection", HttpStatus.SC_MOVED_PERMANENTLY)
              response.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))
              response.addHeader(new BasicHeader("Location", "lkjlasdoifljsf"))
              
              when(conn.isResponseAvailable(anyInt)).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(response)
            })

            intercept[RedirectException] {

              val result = action match {
                  case Get => f.client.get(mockUri)
                  case GetAsBytes => f.client.getAsBytes(mockUri)
                  case GetAsString => f.client.getAsString(mockUri)
              }
            }

            verify(f.conn, times(21)).receiveResponseHeader
        }
    }

    test("handle redirect to invalid uri with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) => 
            {
              val response = prepareHttpResponse("Redirection", HttpStatus.SC_MOVED_PERMANENTLY)
              response.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))
              response.addHeader(new BasicHeader("Location", "lkjlasdoifljsf"))

              when(conn.isResponseAvailable(anyInt)).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(response)
            })

            intercept[RedirectException] {

              val result = action match {
                  case Get => f.client.get(mockUri, additionalHeader)
                  case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                  case GetAsString => f.client.getAsString(mockUri, additionalHeader)
              }
            }

            verify(f.conn, times(21)).receiveResponseHeader
        }
 
    }

    test("response available with redirect status code") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) =>
            {
              var redirectResponse = prepareHttpResponse("Redirection", HttpStatus.SC_MULTIPLE_CHOICES)
              redirectResponse.addHeader(new BasicHeader("Location", "http://test2.metl.com/redirect.xml"))

              var contentResponse = prepareHttpResponse("Whatever", HttpStatus.SC_OK)
              contentResponse.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))

              when(conn.isResponseAvailable(anyInt)).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(redirectResponse).thenReturn(contentResponse)
            })
         
            val result = action match {
                case Get => f.client.get(mockUri)
                case GetAsBytes => f.client.getAsBytes(mockUri)
                case GetAsString => f.client.getAsString(mockUri)
            }

            assert(result === expectedResult)
            verify(f.connMgr, times(2)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("response available with redirect status code with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) =>
            {
              var redirectResponse = prepareHttpResponse("Redirection", HttpStatus.SC_MULTIPLE_CHOICES)
              redirectResponse.addHeader(new BasicHeader("Location", "http://test2.metl.com/redirect.xml"))

              var contentResponse = prepareHttpResponse("Whatever", HttpStatus.SC_OK)
              contentResponse.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))

              when(conn.isResponseAvailable(anyInt)).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(redirectResponse).thenReturn(contentResponse)
            })
         
            val result = action match {
                case Get => f.client.get(mockUri, additionalHeader)
                case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                case GetAsString => f.client.getAsString(mockUri, additionalHeader)
            }

            assert(result === expectedResult)
            verify(f.connMgr, times(2)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("response available after socket timeout") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) =>
            {
              var contentResponse = prepareHttpResponse("Whatever", HttpStatus.SC_OK)
              contentResponse.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))

              when(conn.isResponseAvailable(anyInt)).thenReturn(false).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(contentResponse)
            })
         
            val result = action match {
                case Get => f.client.get(mockUri)
                case GetAsBytes => f.client.getAsBytes(mockUri)
                case GetAsString => f.client.getAsString(mockUri)
            }
         
            assert(result === expectedResult)
            verify(f.connMgr, times(2)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("response available after socket timeout with additional header") { 
        forAll (getFunctions) { (action, expectedResult) =>
            val f = clientWithCustomResponse((conn) =>
            {
              var contentResponse = prepareHttpResponse("Whatever", HttpStatus.SC_OK)
              contentResponse.addHeader(new BasicHeader("Set-Cookie", "UserID=testing"))

              when(conn.isResponseAvailable(anyInt)).thenReturn(false).thenReturn(true)
              when(conn.receiveResponseHeader).thenReturn(contentResponse)
            })
         
            val result = action match {
                case Get => f.client.get(mockUri, additionalHeader)
                case GetAsBytes => f.client.getAsBytes(mockUri, additionalHeader)
                case GetAsString => f.client.getAsString(mockUri, additionalHeader)
            }
         
            assert(result === expectedResult)
            verify(f.connMgr, times(2)).releaseConnection(any(classOf[ManagedClientConnection]), anyInt, any(classOf[TimeUnit]))
        }
    }

    test("post bytes using the connection") { 

        val f = clientWithResponse("Whatever", HttpStatus.SC_OK)
        val result = f.client.postBytes(mockUri, Array.empty[Byte])
        assert(result === "Whatever".toCharArray.map(_.toByte))

        verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
        verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
        verify(f.conn).flush
    }

    test("post bytes using the connection with additional header") { 

        val f = clientWithResponse("Whatever", HttpStatus.SC_OK)
        val result = f.client.postBytes(mockUri, Array.empty[Byte], additionalHeader)
        assert(result === "Whatever".toCharArray.map(_.toByte))

        verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
        verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
        verify(f.conn).flush
    }

    test("post form using the connection") { 

      val f = clientWithResponse("Whatever", HttpStatus.SC_OK)
         
      val result = f.client.postForm(mockUri, List(("FirstName", "Bob"), ("LastName", "Barry"), ("Age", "35")))
      assert(result === "Whatever".toCharArray.map(_.toByte))

      verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
      verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
      verify(f.conn).flush
    }

    test("post form using the connection with additional header") { 

      val f = clientWithResponse("Whatever", HttpStatus.SC_OK)
         
      val result = f.client.postForm(mockUri, List(("FirstName", "Bob"), ("LastName", "Barry"), ("Age", "35")), additionalHeader)
      assert(result === "Whatever".toCharArray.map(_.toByte))

      verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
      verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
      verify(f.conn).flush
    }

    test("post unencoded form using the connection") { 

        val f = clientWithResponse("Whatever", HttpStatus.SC_OK)

        val result = f.client.postUnencodedForm(mockUri, List(("FirstName", "Bob"), ("LastName", "Barry"), ("Age", "35")))
        assert(result === "Whatever".toCharArray.map(_.toByte))

        verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
        verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
        verify(f.conn).flush
    }

    test("post unencoded form using the connection with additional header") { 

        val f = clientWithResponse("Whatever", HttpStatus.SC_OK)

        val result = f.client.postUnencodedForm(mockUri, List(("FirstName", "Bob"), ("LastName", "Barry"), ("Age", "35")), additionalHeader)
        assert(result === "Whatever".toCharArray.map(_.toByte))

        verify(f.conn).sendRequestHeader(any(classOf[HttpRequest]))
        verify(f.conn).sendRequestEntity(any(classOf[HttpEntityEnclosingRequest]))
        verify(f.conn).flush
    }
}
