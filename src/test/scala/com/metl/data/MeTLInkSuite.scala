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

class MeTLInkSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLInkMatchers with MeTLDataGenerators {
	
	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("convert metl ink to xml") {
		forAll (genInk) { (genInk: MeTLInk) =>
			
			val xml = xmlSerializer.fromMeTLInk(genInk)

			genInk should have (
				server (ServerConfiguration.empty),
				author ((xml \\ "author").text),
				target ((xml \\ "target").text),
				privacy (Privacy.parse((xml \\ "privacy").text)),
				slide ((xml \\ "slide").text),
				identity ((xml \\ "identity").text),
				points (PointConverter.fromText((xml \\ "points").text)),
				checksum (tryo((xml \\ "checksum").text.toDouble).openOr(0.0)),
				startingSum (tryo((xml \\ "startingSum").text.toDouble).openOr(0.0)),
				thickness (tryo((xml \\ "thickness").text.toDouble).openOr(0.0)),
				color (ColorConverter.fromText((xml \\ "color").text)),
				isHighlighter (tryo((xml \\ "highlight").text.toBoolean).openOr(false))
			)
		}
	}

	test("extract metl ink from xml") {

		val content = <message>
						<ink>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>private</privacy>
						  <slide>4</slide>
						  <identity>eecrole:223445834582</identity>
						  <checksum>234235.234234</checksum>
						  <startingSum>233453.1498</startingSum>
						  <points>123.34 234 23</points>
						  <color>#ffff0000</color>
						  <thickness>40.0</thickness>
						  <highlight>false</highlight>
						</ink>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLInk]

		result should have(
			server (ServerConfiguration.empty),
			author ("eecrole"),
			target ("test"),
			privacy (Privacy.PRIVATE),
			slide ("4"),
			identity ("eecrole:223445834582"),
			checksum (234235.234234), 
			startingSum (233453.1498),
			points (List(Point(123.34,234,23))), 
			color (Color(255, 255, 0, 0)),
			thickness (40.0), 
			isHighlighter (false)
		)
	}

	test("extract metl dirty ink from xml") {

		val content = <message>
						<dirtyInk>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>public</privacy>
						  <slide>4</slide>
						  <identity>metlDirtyInk</identity>
						</dirtyInk>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLDirtyInk]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			target ("test"),
			privacy (Privacy.PUBLIC),
			slide ("4"),
			identity ("metlDirtyInk")
		)
	}

    test("serialize MeTLDirtyInk to xml") {
        forAll (genDirtyInk) { (genDirtyInk: MeTLDirtyInk) =>

            implicit val xml = xmlSerializer.fromMeTLDirtyInk(genDirtyInk)

            genDirtyInk should have (
                server (ServerConfiguration.empty),
                author (queryXml[String]("author")),
                target (queryXml[String]("target")),
                privacy (queryXml[Privacy]("privacy")),
                slide (queryXml[String]("slide")),
                identity (queryXml[String]("identity"))
            )
        }
    }
}
