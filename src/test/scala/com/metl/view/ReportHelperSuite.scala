package com.metl.view

import org.scalatest.{FunSuite, MustMatchers}
import org.scalatest.concurrent.AsyncAssertions

class ReportHelperSuite extends FunSuite with AsyncAssertions with MustMatchers {

  test("duration calculated for enter and exit") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false)
    ))
    assert(result === (4,false))
  }

  test("duration calculated for enter and exit, enter and exit") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result === (9,false))
  }

  test("duration calculated for enter and exit, enter") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true)
    ))
    assert(result === (4,true))
  }

  test("duration calculated for enter and exit, enter, enter") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = true)
    ))
    assert(result === (9,true))
  }

  test("duration calculated for exit, enter, enter, exit") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = false),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result === (7,false))
  }

  test("duration calculated for enter, enter, enter, exit") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result === (14,false))
  }

  test("excessive duration calculated for enter, exit") {
    val result = ReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000000, present = false)
    ))
    assert(result === (300,false))
  }
}