package com.metl.data

import org.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.{ShouldMatchers, HavePropertyMatcher, HavePropertyMatchResult}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.OptionValues._
import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

import net.liftweb.util.Helpers._
import net.liftweb.common._
import scala.xml._
import com.metl.data._
import Privacy._

class MeTLMoveDeltaSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLMoveDeltaMatchers with MeTLDataGenerators {
	
	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract metl move delta from xml") {

		val content = <message>
						<moveDelta>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>public</privacy>
						  <slide>4</slide>
						  <identity>metlMoveDelta</identity>
						  <inkIds>
							<inkId>1</inkId>
							<inkId>2</inkId>
							<inkId>3</inkId>
						  </inkIds>
						  <textIds>
							<textId>4</textId>
							<textId>5</textId>
							<textId>6</textId>
						  </textIds>
						  <imageIds>
							<imageId>7</imageId>
							<imageId>8</imageId>
							<imageId>9</imageId>
						  </imageIds>
						  <xTranslate>142.4</xTranslate>
						  <yTranslate>265.2</yTranslate>
						  <xScale>2.0</xScale>
						  <yScale>4.0</yScale>
						  <newPrivacy>private</newPrivacy>
						  <isDeleted>false</isDeleted>
						</moveDelta>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLMoveDelta]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			target ("test"),
			privacy (Privacy.PUBLIC),
			slide ("4"),
			identity ("metlMoveDelta"),
			inkIds (Seq("1", "2", "3")),
			textIds (Seq("4", "5", "6")),
			imageIds (Seq("7", "8", "9")),
			xTranslate (142.4),
			yTranslate (265.2),
			xScale (2.0),
			yScale (4.0),
			newPrivacy (Privacy.PRIVATE),
			isDeleted (false)
		)
	}

	test("serialize MeTLMoveDelta to xml") {
		forAll (genMoveDelta) { (genMoveDelta: MeTLMoveDelta) =>
		
			val xml = xmlSerializer.fromMeTLMoveDelta(genMoveDelta)

			genMoveDelta should have (
				server (ServerConfiguration.empty),
				author ((xml \\ "author").text),
				target ((xml \\ "target").text),
				privacy (Privacy.parse((xml \\ "privacy").text)),
				slide ((xml \\ "slide").text),
				identity ((xml \\ "identity").text),
				inkIds ((xml \\ "inkId").map(_.text)),
				textIds ((xml \\ "textId").map(_.text)),
				imageIds ((xml \\ "imageId").map(_.text)),
				xTranslate (tryo((xml \\ "xTranslate").text.toDouble).openOr(0.0)),
				yTranslate (tryo((xml \\ "yTranslate").text.toDouble).openOr(0.0)),
				xScale (tryo((xml \\ "xScale").text.toDouble).openOr(0.0)),
				yScale (tryo((xml \\ "yScale").text.toDouble).openOr(0.0)),
				newPrivacy (Privacy.parse((xml \\ "newPrivacy").text)),
				isDeleted (tryo((xml \\ "isDeleted").text.toBoolean).openOr(false))
			)
		}
	}
}
