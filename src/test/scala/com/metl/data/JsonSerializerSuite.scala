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
import Privacy._

import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

import net.liftweb.json._
import net.liftweb.json.Serialization.{read, write}

class JsonSerializerHelperSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLDataGenerators {

  implicit val formats = Serialization.formats(NoTypeHints) + new PrivacySerializer
  class InternalJsonSerializerHelper extends JsonSerializerHelper
  var jsonHelper: JsonSerializerHelper = _

  before {
    jsonHelper = new InternalJsonSerializerHelper()
  }

  val genListJDbl = for {
    dbl <- Gen.containerOfN[List, Double](1,arbitrary[Double])
  } yield dbl.map(d => JDouble(d))

  val genListJStr = for {
    str <- Gen.containerOfN[List, String](1,arbitrary[String])
  } yield str.map(s => JString(s))

  val genJObjListStr = for {
    listStr <- genListJStr
  } yield JObject(JField("name", JArray(listStr)) :: Nil)

  val genJObjListDbl = for {
    listDbl <- genListJDbl
  } yield JObject(JField("name", JArray(listDbl)) :: Nil)

  val genJObjString = for {
    str <- arbitrary[String]
  } yield (JObject(JField("name", JString(str)) :: Nil), str)

  val genJObjBool = for {
    bool <- arbitrary[Boolean]
  } yield (JObject(JField("name", JBool(bool)) :: Nil), bool)

  val genJObjBigInt = for {
    bigint <- arbitrary[BigInt]
  } yield (JObject(JField("name", JInt(bigint)) :: Nil), bigint)

  val genJObjInt = for {
    int <- arbitrary[Int]
  } yield (JObject(JField("name", JInt(int)) :: Nil), int)

  val genJObjLong = for {
    long <- arbitrary[Long]
  } yield (JObject(JField("name", JInt(long)) :: Nil), long)

  val genJObjDbl = for {
    dbl <- arbitrary[Double]
  } yield (JObject(JField("name", JDouble(dbl)) :: Nil), dbl)

  val genJObjPrivacy = for {
    privacy <- genPrivacy
  } yield (JObject(JField("name", JString(privacy.toString.toLowerCase)) :: Nil), privacy)

  test("extract string from json object") {
    forAll (genJObjString) { (genStr: Tuple2[JObject, String]) =>

      (genStr._1 \ "name").extract[String] should equal(genStr._2)
      jsonHelper.getStringByName(genStr._1, "name") should equal(genStr._2)
      jsonHelper.getStringByName(genStr._1, "name") should equal((genStr._1 \ "name").extract[String])
    }
  }

  test("extract bool from json object") {
    forAll (genJObjBool) { (genBool: Tuple2[JObject, Boolean]) =>

      (genBool._1 \ "name").extract[Boolean] should equal(genBool._2)
      jsonHelper.getBooleanByName(genBool._1, "name") should equal(genBool._2)
      jsonHelper.getBooleanByName(genBool._1, "name") should equal((genBool._1 \ "name").extract[Boolean])
    }
  }

  test("extract double from json object") {
    forAll (genJObjDbl) { (genDbl: Tuple2[JObject, Double]) =>

      (genDbl._1 \ "name").extract[Double] should equal(genDbl._2)
      jsonHelper.getDoubleByName(genDbl._1, "name") should equal(genDbl._2)
      jsonHelper.getDoubleByName(genDbl._1, "name") should equal((genDbl._1 \ "name").extract[Double])
    }
  }

  test("extract bigint from json object") {
    forAll (genJObjBigInt) { (genBigInt: Tuple2[JObject, BigInt]) =>

      (genBigInt._1 \ "name").extract[BigInt] should equal(genBigInt._2)
    }
  }

  test("extract int from json object") {
    forAll (genJObjInt) { (genInt: Tuple2[JObject, Int]) =>

      (genInt._1 \ "name").extract[Int] should equal(genInt._2)
      jsonHelper.getIntByName(genInt._1, "name") should equal(genInt._2)
      jsonHelper.getIntByName(genInt._1, "name") should equal((genInt._1 \ "name").extract[Int])
    }
  }

  test("extract long from json object") {
    forAll (genJObjLong) { (genLong: Tuple2[JObject, Long]) =>

      (genLong._1 \ "name").extract[Long] should equal(genLong._2)
      jsonHelper.getLongByName(genLong._1, "name") should equal(genLong._2)
      jsonHelper.getLongByName(genLong._1, "name") should equal((genLong._1 \ "name").extract[Long])
    }
  }

  test("extract privacy from json object") {
    forAll (genJObjPrivacy) { (genPrivacy: Tuple2[JObject, Privacy]) =>

      (genPrivacy._1 \ "name").extract[Privacy] should equal(genPrivacy._2)
      jsonHelper.getPrivacyByName(genPrivacy._1, "name") should equal(genPrivacy._2)
      jsonHelper.getPrivacyByName(genPrivacy._1, "name") should equal((genPrivacy._1 \ "name").extract[Privacy])
    }
  }

  test("extract list of doubles from json object") {
    forAll (genJObjListDbl) { (genListDbl: JObject) =>

      jsonHelper.getListOfDoublesByName(genListDbl, "name") should equal((genListDbl \ "name").extract[List[Double]])
    }
  }

  test("extract list of strings from json object") {
    forAll (genJObjListStr) { (genListStr: JObject) =>

      jsonHelper.getListOfStringsByName(genListStr, "name") should equal((genListStr \ "name").extract[List[String]])
    }
  }
}

class JsonSerializerSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLMoveDeltaMatchers with MeTLDataGenerators {

  var jsonSerializer: JsonSerializer = _

  before {
    jsonSerializer = new JsonSerializer(EmptyBackendAdaptor)
  }

  test("parse metl move delta to json and back") {
    forAll (genMoveDelta) { (gennedMoveDelta: MeTLMoveDelta) =>

      val json = jsonSerializer.fromMeTLMoveDelta(gennedMoveDelta)
      val md = jsonSerializer.toMeTLMoveDelta(json)

      md should equal(gennedMoveDelta)
    }
  }

  test("parse metl ink to json and back") {
    forAll (genInk) { (gennedInk: MeTLInk) =>

      val json = jsonSerializer.fromMeTLInk(gennedInk)
      val ink = jsonSerializer.toMeTLInk(json)

      ink should equal(gennedInk)
    }
  }

  test("parse metl image to json and back") {
    forAll (genImage) { (gennedImage: MeTLImage) =>

      val json = jsonSerializer.fromMeTLImage(gennedImage)
      val image = jsonSerializer.toMeTLImage(json)
      //info("toMeTLImage: Creates Full(Array.empty[Byte]) instead of Empty, and Array instead of WrappedArray")

      image should equal(gennedImage)
    }
  }

  test("parse metl text to json and back") {
    forAll (genText) { (gennedText: MeTLText) =>

      val json = jsonSerializer.fromMeTLText(gennedText)
      val text = jsonSerializer.toMeTLText(json)

      text should equal(gennedText)
    }
  }

  test("parse metl multi-word text to json and back") {
    forAll (genMultiWordText) { (gennedText: MeTLMultiWordText) =>

      val json = jsonSerializer.fromMeTLMultiWordText(gennedText)
      val text = jsonSerializer.toMeTLMultiWordText(json)

      text should equal(gennedText)
    }
  }

  test("parse history with 2 multiWordTexts to json and back returning the last one") {
    forAll (genMultiWordText) { (gennedText: MeTLMultiWordText) => {
      val h = new History("test")
      val gennedText2 = gennedText.copy(timestamp = gennedText.timestamp + 2, words = gennedText.words.map(w => w.copy(text = w.text + "_copied")))
      val md = MeTLMoveDelta(gennedText.server,gennedText.author,gennedText.timestamp + 4,gennedText.target,gennedText.privacy,gennedText.slide,"testMd",100.0,100.0,Nil,Nil,List(gennedText.identity),Nil,Nil,-50.0,-50.0,1.0,1.0,Privacy.NOT_SET,false)
      h.addStanza(gennedText)
      h.addStanza(gennedText2)
      h.addStanza(gennedText.copy(timestamp = gennedText.timestamp + 1))
      h.addStanza(gennedText.copy(timestamp = gennedText.timestamp - 1))
      h.addStanza(md)
      val sh = jsonSerializer.fromHistory(h)
      (sh \\ "multiWordTexts").children.toList should equal(List(JField(gennedText2.identity,jsonSerializer.fromMeTLData(md.adjustIndividualContent(gennedText2)))))
      ((sh \\ "multiWordTexts").children.toList.head \\ "timestamp") should equal(JInt(md.timestamp))
      ((sh \\ "multiWordTexts").children.toList.head \\ "words" \\ "text") should equal(JObject(gennedText2.words.map(w => JField("text",JString(w.text))).toList))
    }}
  }

  test("parse metl dirty ink to json and back") {
    forAll (genDirtyInk) { (gennedDirtyInk: MeTLDirtyInk) =>

      val json = jsonSerializer.fromMeTLDirtyInk(gennedDirtyInk)
      val dirtyInk = jsonSerializer.toMeTLDirtyInk(json)

      dirtyInk should equal(gennedDirtyInk)
    }
  }

  test("parse metl dirty image to json and back") {
    forAll (genDirtyImage) { (gennedDirtyImage: MeTLDirtyImage) =>

      val json = jsonSerializer.fromMeTLDirtyImage(gennedDirtyImage)
      val dirtyImage = jsonSerializer.toMeTLDirtyImage(json)

      dirtyImage should equal(gennedDirtyImage)
    }
  }

  test("parse metl dirty text to json and back") {
    forAll (genDirtyText) { (gennedDirtyText: MeTLDirtyText) =>

      val json = jsonSerializer.fromMeTLDirtyText(gennedDirtyText)
      val dirtyText = jsonSerializer.toMeTLDirtyText(json)

      dirtyText should equal(gennedDirtyText)
    }
  }

  test("parse metl command to json and back") {
    forAll (genCommand) { (gennedCommand: MeTLCommand) =>

      val json = jsonSerializer.fromMeTLCommand(gennedCommand)
      val command = jsonSerializer.toMeTLCommand(json)

      command should equal(gennedCommand)
    }
  }

  test("parse submission to json and back") {
    forAll (genSubmission) { (gennedSubmission: MeTLSubmission) =>
      val json = jsonSerializer.fromSubmission(gennedSubmission)
      val submission = jsonSerializer.toSubmission(json)
      submission should equal(gennedSubmission)
    }
  }
  test("parse grade to json and back") {
    forAll (genGrade) { (gennedGrade: MeTLGrade) => {
      val json = jsonSerializer.fromGrade(gennedGrade)
      val grade = jsonSerializer.toGrade(json)
      grade should equal(gennedGrade)
    }}
  }
  test("parse numericGradeValue to json and back") {
    forAll (genNumericGradeValue) { (gennedGradeValue: MeTLNumericGradeValue) => {
      val json = jsonSerializer.fromNumericGradeValue(gennedGradeValue)
      val gradeValue = jsonSerializer.toNumericGradeValue(json)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse booleanGradeValue to json and back") {
    forAll (genBooleanGradeValue) { (gennedGradeValue: MeTLBooleanGradeValue) => {
      val json = jsonSerializer.fromBooleanGradeValue(gennedGradeValue)
      val gradeValue = jsonSerializer.toBooleanGradeValue(json)
      gradeValue should equal(gennedGradeValue)
    }}
  }
  test("parse textGradeValue to json and back") {
    forAll (genTextGradeValue) { (gennedGradeValue: MeTLTextGradeValue) => {
      val json = jsonSerializer.fromTextGradeValue(gennedGradeValue)
      val gradeValue = jsonSerializer.toTextGradeValue(json)
      gradeValue should equal(gennedGradeValue)
    }}
  }



}
