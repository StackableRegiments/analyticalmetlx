package com.metl.data

import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.{ShouldMatchers, HavePropertyMatcher, HavePropertyMatchResult}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.OptionValues._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import scala.xml._
import com.metl.data._
import com.metl.model._
import Privacy._

class MeTLHistorySuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLTextMatchers with MeTLDataGenerators {
  test("add an ink") {
    val h = new History("test")
    forAll (genInk) { (ink: MeTLInk) =>
      h.addStanza(ink)
      h.getInks == List(ink) || h.getHighlighters == List(ink)
    }
  }
  test("add a textbox") {
    val h = new History("test")
    forAll (genText) { (text: MeTLText) =>
      h.addStanza(text)
      h.getTexts == List(text)
    }
  }
  test("add an image") {
    val h = new History("test",1.0,1.0,0,0, new Object with Chunker {
      def emit(t:Theme,h:History) = {}
      def add(s:MeTLStanza,h:History) = {}
    })
    forAll (genImage) { (image: MeTLImage) =>
      h.addStanza(image)
      h.getImages == List(image)
    }
  }
  test("add a submission") {
    val h = new History("test")
    forAll (genSubmission) { (sub: MeTLSubmission) =>
      h.addStanza(sub)
      h.getSubmissions == List(sub)
    }
  }
  test("add a command") {
    val h = new History("test")
    forAll (genCommand) { (comm: MeTLCommand) =>
      h.addStanza(comm)
      h.getCommands == List(comm)
    }
  }

}
