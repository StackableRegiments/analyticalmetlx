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

class MeTLSubmissionExtractorSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLDataGenerators with QueryXml with MeTLSubmissionMatchers {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("usernames and highlights are nested within blacklist") {

		val content = <message>
						<screenshotSubmission>
						  <author>eecrole</author>
						  <identity>screenshotSubmission</identity>
						  <target>submission</target> 
						  <privacy>public</privacy>
						  <title>blah</title>
						  <slide>3003034</slide>
						  <url>http://test.metl.com/test/submission/metlImage03.png</url>
						  <blacklist>
							<username>eecrole</username>
							<highlight>#ffffffff</highlight>
						  </blacklist>
						  <blacklist>
							<username>jasonp</username>
							<highlight>#ffffff00</highlight>
						  </blacklist>
						</screenshotSubmission>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLSubmission]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			title ("blah"),
			privacy (Privacy.PUBLIC),
			identity ("screenshotSubmission"),
			slideJid (3003034),
			url ("http://test.metl.com/test/submission/metlImage03.png"),
			imageBytes (Full(Array.empty[Byte])),
			blacklist (List(SubmissionBlacklistedPerson("eecrole", Color(0xff, 0xff, 0xff, 0xff)), SubmissionBlacklistedPerson("jasonp", Color(0xff, 0xff, 0xff, 0x00)))),
			target ("submission")
		)
	}

    test("serialize submission to xml") {
        forAll (genSubmission) { (genSubmission: MeTLSubmission) =>

            implicit val xml = xmlSerializer.fromSubmission(genSubmission)

            genSubmission should have (
                server (ServerConfiguration.empty),
                author (queryXml[String]("author")),
                slideJid (queryXml[Int]("slide")),
                title (queryXml[String]("title")),
                target (queryXml[String]("target")),
                privacy (queryXml[Privacy]("privacy")),
                url (queryXml[String]("url"))
            )
        }
    }
}
