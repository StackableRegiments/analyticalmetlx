package com.metl.h2

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

import net.liftweb.db._

class H2SerializerSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLMoveDeltaMatchers with MeTLDataGenerators {

  var h2Serializer: H2Serializer = _

  before {
    h2Serializer = new H2Serializer(EmptyBackendAdaptor)
  }

  test("parse metl move delta to db and back") {
    forAll (genMoveDelta) { (gennedMoveDelta: MeTLMoveDelta) =>

      val db = h2Serializer.fromMeTLMoveDelta(gennedMoveDelta)
      val md = h2Serializer.toMeTLMoveDelta(db)

      md should equal(gennedMoveDelta)
    }
  }

  test("parse metl ink to db and back") {
    forAll (genInk) { (gennedInk: MeTLInk) =>

      val db = h2Serializer.fromMeTLInk(gennedInk)
      val ink = h2Serializer.toMeTLInk(db)

      ink should equal(gennedInk)
    }
  }

  test("parse metl image to db and back") {
    forAll (genImage) { (gennedImage: MeTLImage) =>

      val db = h2Serializer.fromMeTLImage(gennedImage)
      val image = h2Serializer.toMeTLImage(db)
      //info("toMeTLImage: Creates Full(Array.empty[Byte]) instead of Empty, and Array instead of WrappedArray")
      image should equal(gennedImage)
    }
  }

  test("parse metl text to db and back") {
    forAll (genText) { (gennedText: MeTLText) =>

      val db = h2Serializer.fromMeTLText(gennedText)
      val text = h2Serializer.toMeTLText(db)

      text should equal(gennedText)
    }
  }

  test("parse metl multi-word text to db and back") {
    forAll (genMultiWordText) { (gennedText: MeTLMultiWordText) =>

      val db = h2Serializer.fromMeTLMultiWordText(gennedText)
      val text = h2Serializer.toMeTLMultiWordText(db)

      text should equal(gennedText)
    }
  }

  test("parse metl dirty ink to db and back") {
    forAll (genDirtyInk) { (gennedDirtyInk: MeTLDirtyInk) =>

      val db = h2Serializer.fromMeTLDirtyInk(gennedDirtyInk)
      val dirtyInk = h2Serializer.toMeTLDirtyInk(db)

      dirtyInk should equal(gennedDirtyInk)
    }
  }

  test("parse metl dirty image to db and back") {
    forAll (genDirtyImage) { (gennedDirtyImage: MeTLDirtyImage) =>

      val db = h2Serializer.fromMeTLDirtyImage(gennedDirtyImage)
      val dirtyImage = h2Serializer.toMeTLDirtyImage(db)

      dirtyImage should equal(gennedDirtyImage)
    }
  }

  test("parse metl dirty text to db and back") {
    forAll (genDirtyText) { (gennedDirtyText: MeTLDirtyText) =>

      val db = h2Serializer.fromMeTLDirtyText(gennedDirtyText)
      val dirtyText = h2Serializer.toMeTLDirtyText(db)

      dirtyText should equal(gennedDirtyText)
    }
  }

  test("parse metl command to db and back") {
    forAll (genCommand) { (gennedCommand: MeTLCommand) =>

      val db = h2Serializer.fromMeTLCommand(gennedCommand)
      val command = h2Serializer.toMeTLCommand(db)

      command should equal(gennedCommand)
    }
  }

  test("parse submission to db and back") {
    forAll (genSubmission) { (gennedSubmission: MeTLSubmission) =>
      val db = h2Serializer.fromSubmission(gennedSubmission)
      val submission = h2Serializer.toSubmission(db)
      submission should equal(gennedSubmission)
    }
  }
  test("parse grade to db and back") {
    forAll (genGrade) { (gennedGrade: MeTLGrade) => {
      val db = h2Serializer.fromGrade(gennedGrade)
      val grade = h2Serializer.toGrade(db)
      grade should equal(gennedGrade)
    }}
  }
  test("parse numericGradeValue to db and back") {
    forAll (genNumericGradeValue) { (gennedGradeValue: MeTLNumericGradeValue) => {
      val db = h2Serializer.fromNumericGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toNumericGradeValue(db)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse booleanGradeValue to db and back") {
    forAll (genBooleanGradeValue) { (gennedGradeValue: MeTLBooleanGradeValue) => {
      val db = h2Serializer.fromBooleanGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toBooleanGradeValue(db)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse textGradeValue to db and back") {
    forAll (genTextGradeValue) { (gennedGradeValue: MeTLTextGradeValue) => {
      val db = h2Serializer.fromTextGradeValue(gennedGradeValue)
      val gradeValue = h2Serializer.toTextGradeValue(db)
      gradeValue should equal(gennedGradeValue)
    }}
  }



}
