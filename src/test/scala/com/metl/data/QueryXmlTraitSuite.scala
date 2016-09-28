package com.metl.data

import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import com.metl.data._
import Privacy._

class QueryXmlTraitSuite extends FunSuite with ShouldMatchers with QueryXml {

  implicit val xmlContent = 
    <content>
        <privacy>private</privacy>
        <color>#34534fff</color>
        <string>hello world</string>
        <boolean>true</boolean>
        <double>5.634</double>
        <long>234623423</long>
        <int>42</int>
    </content>

  test("pull privacy text out of xml label") {
    queryXml[Privacy]("privacy") should equal(Privacy.PRIVATE)
  }

  test("pull color text out of xml label") {
    queryXml[Color]("color") should equal(Color(0x34, 0x53, 0x4f, 0xff))
  }

  test("pull string text out of xml label") {
    queryXml[String]("string") should equal("hello world")
  }

  test("pull boolean text out of xml label") {
    queryXml[Boolean]("boolean") should equal(true)
  }

  test("pull double text out of xml label") {
    queryXml[Double]("double") should equal(5.634)
  }

  test("pull long text out of xml label") {
    queryXml[Long]("long") should equal(234623423L)
  }

  test("pull int text out of xml label") {
    queryXml[Int]("int") should equal(42)
  }

  test("pull from non-existant label") {
    queryXml[String]("text") should equal("")
  }

  test("pull integer from string label") {
    queryXml[Int]("string") should equal(-1)
  }

  test("pull color from privacy label") {
    queryXml[Color]("privacy") should equal(Color.default)
  }

  test("use unimplemented type") {
    intercept[IllegalArgumentException] {
      queryXml[MeTLInk]("content") 
    }
  }
}
