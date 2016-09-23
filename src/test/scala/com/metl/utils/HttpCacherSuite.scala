package com.metl.utils

import java.text.SimpleDateFormat
import java.util.{Date, GregorianCalendar, Locale, SimpleTimeZone, TimeZone}

import org.scalatest.{BeforeAndAfter, FunSuite}
import net.liftweb.mockweb.MockWeb._
import net.liftweb.mocks.MockHttpServletRequest
import net.liftweb.http._
import net.liftweb.util.Helpers._

class HttpCacherSuite extends FunSuite with BeforeAndAfter {
  val testUrl = "http://test.metl.com/test"
  val binary = CachedBinary(Array.empty[Byte], 1350344248511L)

  def extractHeaderValue(response: BasicResponse, header: String): String = {
    response.headers find { h => h._1 == header } match {
      case Some(etag) => etag._2
      case _ => "Empty"
    }
  }

  var cacher: HttpCacher = _

  before {
    cacher = new HttpCacher
  }

  test("construct 200 response for empty data") {
    testS(testUrl) {
      val response = cacher.constructResponse(binary, "image/jpg",new TimeSpan(10 * 1000))// 10 seconds)
      assert(response.code == 200)
    }
  }

  test("construct appropriate etag for empty data") {
    testS(testUrl) {
      val response = cacher.constructResponse(binary, "image/jpg",new TimeSpan(10 * 1000))// 10 seconds)
      assert(extractHeaderValue(response, "ETag") == binary.checksum)
    }
  }

  test("construct appropriate expires header for empty data") {
    testS(testUrl) {
      val response = cacher.constructResponse(binary, "image/jpg",new TimeSpan(10 * 1000))// 10 seconds)

      val formatter = new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US)
      val calendar = new GregorianCalendar()
      calendar.set(2012, 9, 15, 23, 37, 38)
      calendar.setTimeZone(new SimpleTimeZone(0, "Greenwich"))

      assert(extractHeaderValue(response, "Expires") == formatter.format( calendar.getTime))
    }
  }

  test("construct appropriate cache-control for empty data") {
    testS(testUrl) {
      val response = cacher.constructResponse(binary, "image/jpg",new TimeSpan(10 * 1000))// 10 seconds)
      assert(extractHeaderValue(response, "Cache-Control") == "max-age=0, must-revalidate")
    }
  }

  test("response of 304 if not modified") {
    val req = new MockHttpServletRequest(testUrl)
    req.headers = Map(("If-None-Match", List("da39a3ee5e6b4b0d3255bfef95601890afd80709")))
    testS(req) {
      val binary = CachedBinary(Array.empty[Byte], new Date().getTime)
      val cacher2 = new HttpCacher
      val response = cacher2.constructResponse(binary, "image/jpg", new TimeSpan(10 * 1000))// seconds)
      assert(response.code == 304)
    }
  }
}
