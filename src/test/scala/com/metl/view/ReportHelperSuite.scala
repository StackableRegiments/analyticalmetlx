package com.metl.view

import org.scalatest.{FunSuite, MustMatchers}
import org.scalatest.concurrent.AsyncAssertions

class ReportHelperSuite extends FunSuite with AsyncAssertions with MustMatchers {

  test("duration calculated for enter and exit") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false)
    ))
    assert(result._1 === 4)
    assert(result._2 === false)
  }

  test("duration calculated for enter and exit, enter and exit") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result._1 === 9)
    assert(result._2 === false)
  }

  test("duration calculated for enter and exit, enter") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true)
    ))
    assert(result._1 === 4)
    assert(result._2 === true)
  }

  test("duration calculated for enter and exit, enter, enter") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = true)
    ))
    assert(result._1 === 9)
    assert(result._2 === true)
  }

  test("duration calculated for exit, enter, enter, exit") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = false),
      RowTime(3000, present = true),
      RowTime(5000, present = false),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result._1 === 7)
    assert(result._2 === false)
  }

  test("duration calculated for enter, enter, enter, exit") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000, present = true),
      RowTime(10000, present = true),
      RowTime(15000, present = false)
    ))
    assert(result._1 === 14)
    assert(result._2 === false)
  }

  test("excessive duration calculated for enter, exit") {
    val result = StudentActivityReportHelper.getSecondsOnPage(List(
      RowTime(1000, present = true),
      RowTime(3000000, present = false)
    ))
    assert(result._1 === 300)
    assert(result._2 === false)
  }
}