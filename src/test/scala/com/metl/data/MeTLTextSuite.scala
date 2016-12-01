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

class MeTLTextExtractorSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLTextMatchers with MeTLDataGenerators {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("convert metl text to xml") {
		forAll (genText) { (genText: MeTLText) =>
			
			implicit val xml = xmlSerializer.fromMeTLText(genText)

			genText should have (
				server (ServerConfiguration.empty),
				author (queryXml[String]("author")),
				target (queryXml[String]("target")),
				privacy (queryXml[Privacy]("privacy")),
				slide (queryXml[String]("slide")),
				identity (queryXml[String]("identity")),
				tag (queryXml[String]("tag")),
				caret (queryXml[Int]("caret")), 
				text (queryXml[String]("text")),
				style (queryXml[String]("style")),
				family (queryXml[String]("family")),
				weight (queryXml[String]("weight")),
				size (queryXml[Double]("size")),
				decoration (queryXml[String]("decoration")),
				color (queryXml[Color]("color")),
				width (queryXml[Double]("width")),
				height (queryXml[Double]("height")),
				x (queryXml[Double]("x")),
				y (queryXml[Double]("y"))
			)
		}
	}

	test("extract metl text from xml") {

		val content = <message>
						<textbox>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>private</privacy>
						  <slide>4</slide>
						  <identity>eecrole:223445834582</identity>
						  <tag>eecrole:223445834582</tag>
						  <caret>0</caret>
						  <text>Hello World!</text>
						  <style>Underline</style>
						  <family>Helvetica</family>
						  <weight>Bold</weight>
						  <size>12.0</size>
						  <decoration>Italics</decoration>
						  <color>#ffff0000</color>
						  <width>200</width>
						  <height>100</height>
						  <x>120</x>
						  <y>300</y>
						</textbox>
					  </message>

		val result = xmlSerializer.toMeTLData(content)
		assert(result === MeTLText(ServerConfiguration.empty, "eecrole", -1L, "Hello World!", 100.0, 200.0, 0, 120.0, 300.0, "eecrole:223445834582",
			"Underline", "Helvetica", "Bold", 12.0, "Italics", "eecrole:223445834582", "test", Privacy.PRIVATE, "4", Color(255, 255, 0, 0)))
	}

	test("extract metl dirty text from xml") {

		val content = <message>
						<dirtyText>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>public</privacy>
						  <slide>4</slide>
						  <identity>metlDirtyText</identity>
						</dirtyText>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLDirtyText]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			target ("test"),
			privacy (Privacy.PUBLIC),
			slide ("4"),
			identity ("metlDirtyText")
		)
	}

    test("serialize metl dirty text to xml") {

		forAll (genDirtyText) { (genDirtyText: MeTLDirtyText) =>
			
			implicit val xml = xmlSerializer.fromMeTLDirtyText(genDirtyText)

			genDirtyText should have (
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
