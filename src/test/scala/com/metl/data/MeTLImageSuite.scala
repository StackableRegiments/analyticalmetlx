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

class MeTLImageSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLImageMatchers with MeTLDataGenerators with Logger {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract metl image from xml") {

		val content = <message>
						<image>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>private</privacy>
						  <slide>4</slide>
						  <identity>metlImage</identity>
						  <tag>eecrole:223445834582</tag>
						  <source>http://test.metl.com/test/testimg23435.png</source>
						  <width>200</width>
						  <height>100</height>
						  <x>120</x>
						  <y>300</y>
						</image>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLImage]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			tag ("eecrole:223445834582"),
			source (Full("http://test.metl.com/test/testimg23435.png")),
			imageBytes (Full(Array.empty[Byte])),
			pngBytes (Empty),
			width (200.0),
			height (100.0),
			x (120.0),
			y (300.0),
			target ("test"),
			privacy (Privacy.PRIVATE),
			slide ("4"),
			identity ("metlImage")
		)
	}

	test("serialize MeTLImage to xml") {
		forAll (genImage) { (genImage: MeTLImage) =>
	
    val xml = {
      try {
        xmlSerializer.fromMeTLImage(genImage)
      } catch {
        case e:Throwable => {
          error("EXCEPTION",e)
          NodeSeq.Empty
        }
      }
    }
			genImage should have (
				server (ServerConfiguration.empty),
				author ((xml \\ "author").text),
				target ((xml \\ "target").text),
				privacy (Privacy.parse((xml \\ "privacy").text)),
				slide ((xml \\ "slide").text),
				identity ((xml \\ "identity").text),
				tag ((xml \\ "tag").text),
				source (Full((xml \\ "source").text)),
				x (tryo((xml \\ "x").text.toDouble).openOr(0.0)),
				y (tryo((xml \\ "y").text.toDouble).openOr(0.0)),
				width (tryo((xml \\ "width").text.toDouble).openOr(0.0)),
				height (tryo((xml \\ "height").text.toDouble).openOr(0.0))
			)
		}
	}

	test("extract metl dirty image from xml") {

		val content = <message>
						<dirtyImage>
						  <author>eecrole</author>
						  <target>test</target>
						  <privacy>public</privacy>
						  <slide>4</slide>
						  <identity>metlDirtyImage</identity>
						</dirtyImage>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLDirtyImage]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			target ("test"),
			privacy (Privacy.PUBLIC),
			slide ("4"),
			identity ("metlDirtyImage")
		)
	}

    test("serialize MeTLDirtyImage to xml") {
        forAll (genDirtyImage) { (genDirtyImage: MeTLDirtyImage) =>

          implicit val xml = xmlSerializer.fromMeTLDirtyImage(genDirtyImage)

          genDirtyImage should have (
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
