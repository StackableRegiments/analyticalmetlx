package com.metl.model

import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.{ShouldMatchers, HavePropertyMatcher, HavePropertyMatchResult}
import org.scalatest.OptionValues._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import com.metl.model._


class ChunkerSuite extends FunSuite with ShouldMatchers {
  val chunker: ChunkAnalyzer = new ChunkAnalyzer

  test("extract urls from text") {
    val t = "Go to: http://youtube.com?query=pandas or https://this.that.com.  Any questions?  Ask me here: help@dogfood.com.  (mailto:help@dogfood.com) Get excited!  (Another one is here: this.that/students)"
    val r = chunker.urls(t).toList
    r should equal(List(
      "http://youtube.com?query=pandas",
      "https://this.that.com",
      "mailto:help@dogfood.com",
      "this.that/students"
    ))
  }
}
