package com.metl.utils

import java.time.ZoneId
import java.util.Date

import org.scalatest._

class MultiFormatDateFormatterSuite extends FunSuite {

  val testData = List(
    ("Tue Aug 01 01:22:46 UTC 2017", "1 Aug 2017 01:22:46 GMT"),
    ("8/1/2017 11:41:53 AM", "1 Aug 2017 15:41:53 GMT"),
    ("Sat Jan 23 11:29:20 AEDT 2016", "23 Jan 2016 00:29:20 GMT"),
    ("11/17/2015 1:35:40 PM", "17 Nov 2015 18:35:40 GMT"),
    ("Thu Nov 05 22:22:34 EST 2015", "6 Nov 2015 03:22:34 GMT")
  )
  protected val dateFormat = "EEE MMM dd kk:mm:ss z yyyy" // standard java format
  protected val dateFormatMeTL2011us1 = "MM/dd/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011us2 = "MM/d/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011us3 = "M/dd/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011us4 = "M/d/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011au1 = "dd/MM/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011au4 = "d/MM/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011au2 = "dd/M/yyyy h:mm:ss a"
  protected val dateFormatMeTL2011au3 = "d/M/yyyy h:mm:ss a"
  protected val usZone: ZoneId = ZoneId.of("America/New_York")

  test("format string should object to unspecified timezone") {
    try {
      new MultiFormatDateFormatter(Left("dd/MM/yyyy hh:mm:ss a"))
      assert(false, "no exception thrown")
    } catch {
      case e: IllegalArgumentException => assert(true, "correct assertion, this is a pass")
      case _ => assert(false, "wrong exception thrown")
    }
  }
  test("parser should return correct datetime") {
    assertResult("18 Nov 1982 00:00:00 GMT")(new Date(new MultiFormatDateFormatter(Left("dd/MM/yyyy z")).parse("18/11/1982 UTC")).toGMTString)
  }
  test("parser should return correct datetimes for the test data") {
    val df = new MultiFormatDateFormatter(
      Left(dateFormat),
      Right(dateFormatMeTL2011us1, usZone),
      Right(dateFormatMeTL2011us2, usZone),
      Right(dateFormatMeTL2011us3, usZone),
      Right(dateFormatMeTL2011us4, usZone),
      Right(dateFormatMeTL2011au1, usZone),
      Right(dateFormatMeTL2011au2, usZone),
      Right(dateFormatMeTL2011au3, usZone),
      Right(dateFormatMeTL2011au4, usZone)
    )
    testData.foreach(td => {
      assertResult(td._2, "from input: %s".format(td._1))(new Date(df.parse(td._1)).toGMTString)
    })
  }
  test("formats should be applied in correct order (US first)") {
    val df = new MultiFormatDateFormatter(
      Right(dateFormatMeTL2011us1, usZone),
      Right(dateFormatMeTL2011au1, usZone)
    )
    val input = "08/01/2017 11:41:53 AM"
    assertResult("1 Aug 2017 15:41:53 GMT", "from input: %s".format(input))(new Date(df.parse(input)).toGMTString)
  }
}
