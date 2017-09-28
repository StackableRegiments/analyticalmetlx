package com.metl.data

import com.metl.h2._
import com.metl.h2.dbformats._

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
import Privacy._

import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

class H2SerializerSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLMoveDeltaMatchers with MeTLDataGenerators {

  var h2Serializer: H2Serializer = _

  before {
    h2Serializer = new H2Serializer(EmptyBackendAdaptor)
  }

  test("parse metl move delta to h2 and back") {
    forAll (genMoveDelta) { (gennedMoveDelta: MeTLMoveDelta) =>

      val h2 = h2Serializer.fromMeTLMoveDelta(gennedMoveDelta)
      val md = h2Serializer.toMeTLMoveDelta(h2)

      md should equal(gennedMoveDelta)
    }
  }

  test("parse metl ink to h2 and back") {
    forAll (genInk) { (gennedInk: MeTLInk) =>

      val h2 = h2Serializer.fromMeTLInk(gennedInk)
      val ink = h2Serializer.toMeTLInk(h2)

      ink should equal(gennedInk)
    }
  }

  test("parse metl image to h2 and back") {
    forAll (genImage) { (gennedImage: MeTLImage) =>

      val h2 = h2Serializer.fromMeTLImage(gennedImage)
      val image = h2Serializer.toMeTLImage(h2)
      //info("toMeTLImage: Creates Full(Array.empty[Byte]) instead of Empty, and Array instead of WrappedArray")

      image should equal(gennedImage)
    }
  }

  test("parse metl text to h2 and back") {
    forAll (genText) { (gennedText: MeTLText) =>

      val h2 = h2Serializer.fromMeTLText(gennedText)
      val text = h2Serializer.toMeTLText(h2)

      text should equal(gennedText)
    }
  }

  test("parse metl multi-word text to h2 and back") {
    forAll (genMultiWordText) { (gennedText: MeTLMultiWordText) =>

      val h2 = h2Serializer.fromMeTLMultiWordText(gennedText)
      val text = h2Serializer.toMeTLMultiWordText(h2)

      text should equal(gennedText)
    }
  }

  test("parse metl dirty ink to h2 and back") {
    forAll (genDirtyInk) { (gennedDirtyInk: MeTLDirtyInk) =>

      val h2 = h2Serializer.fromMeTLDirtyInk(gennedDirtyInk)
      val dirtyInk = h2Serializer.toMeTLDirtyInk(h2)

      dirtyInk should equal(gennedDirtyInk)
    }
  }

  test("parse metl dirty image to h2 and back") {
    forAll (genDirtyImage) { (gennedDirtyImage: MeTLDirtyImage) =>

      val h2 = h2Serializer.fromMeTLDirtyImage(gennedDirtyImage)
      val dirtyImage = h2Serializer.toMeTLDirtyImage(h2)

      dirtyImage should equal(gennedDirtyImage)
    }
  }

  test("parse metl dirty text to h2 and back") {
    forAll (genDirtyText) { (gennedDirtyText: MeTLDirtyText) =>

      val h2 = h2Serializer.fromMeTLDirtyText(gennedDirtyText)
      val dirtyText = h2Serializer.toMeTLDirtyText(h2)

      dirtyText should equal(gennedDirtyText)
    }
  }

  test("parse metl command to h2 and back") {
    forAll (genCommand) { (gennedCommand: MeTLCommand) =>

      val h2 = h2Serializer.fromMeTLCommand(gennedCommand)
      val command = h2Serializer.toMeTLCommand(h2)

      command should equal(gennedCommand)
    }
  }

  test("parse submission to h2 and back") {
    forAll (genSubmission) { (gennedSubmission: MeTLSubmission) =>
      val h2 = h2Serializer.fromSubmission(gennedSubmission)
      val submission = h2Serializer.toSubmission(h2)
      submission should equal(gennedSubmission)
    }
  }
  test("parse grade to h2 and back") {
    forAll (genGrade) { (gennedGrade: MeTLGrade) => {
      val h2 = h2Serializer.fromGrade(gennedGrade)
      val grade = h2Serializer.toGrade(h2)
      grade should equal(gennedGrade)
    }}
  }
  test("parse numericGradeValue to h2 and back") {
    forAll (genNumericGradeValue) { (gennedGradeValue: MeTLNumericGradeValue) => {
      val h2 = h2Serializer.fromNumericGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toNumericGradeValue(h2)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse booleanGradeValue to h2 and back") {
    forAll (genBooleanGradeValue) { (gennedGradeValue: MeTLBooleanGradeValue) => {
      val h2 = h2Serializer.fromBooleanGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toBooleanGradeValue(h2)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse textGradeValue to h2 and back") {
    forAll (genTextGradeValue) { (gennedGradeValue: MeTLTextGradeValue) => {
      val h2 = h2Serializer.fromTextGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toTextGradeValue(h2)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse conversation to h2 and back") {
    forAll (genConversation) { (conv:Conversation) => {

      val thisSer = new H2Serializer(EmptyBackendAdaptor){
        override def enrichSlidesForConversation(c:H2Conversation,slides:List[Slide]):List[H2Slide] = {
          conv.slides.map(s => fromSlide(s))
        }
      }
      val ser = thisSer.fromConversation(conv)
      val res = thisSer.toConversation(ser)
      conv should equal(res)
    }}
  }
  test("parse slide to h2 and back") {
    forAll (genSlide) { (s:Slide) => {
      val indexless = s.copy(index = 0,exposed = false) // h2 ignores the index on individual slides when serializing them.  That's the job of the conversation, not the slide now.
      val ser = h2Serializer.fromSlide(indexless)
      val res = h2Serializer.toSlide(ser)
      indexless should equal(res.copy(index = 0,exposed = false))
    }}
  }
  test("parse wootOperation to h2 and back") {
    forAll (genWootOperation) { (s:WootOperation) => {
      val ser = h2Serializer.fromWootOperation(s)
      val res = h2Serializer.toWootOperation(ser)
      s should equal(res)
    }}
  }
  test("parse forumPost to h2 and back") {
    forAll (genForumPost) { (s:ForumPost) => {
      val ser = h2Serializer.fromForumPost(s)
      val res = h2Serializer.toForumPost(ser)
      s should equal(res)
    }}
  }
}
