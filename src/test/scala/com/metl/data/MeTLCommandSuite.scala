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

class MeTLCommandSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with MeTLDataGenerators with QueryXml with MeTLCommandMatchers {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract command from xml") {

		val content = <message>
            <command>
							<author>eecrole</author>
							<name>GO_TO_SLIDE</name>
							<parameters>
								<parameter>2234234</parameter>
							</parameters>
						</command>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLCommand]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			command ("GO_TO_SLIDE"),
			commandParameters (List("2234234"))
		)
	}

    test("serialize MeTLCommand to xml") {
        forAll (genCommand) { (genComm: MeTLCommand) =>

            implicit val xml = xmlSerializer.fromMeTLCommand(genComm)

            genComm should have (
               author (queryXml[String]("author")),
               command (queryXml[String]("name")),
               commandParameters (xmlSerializer.getListOfStringsByNameWithin(xml, "parameter", "parameters"))
            )
        }
    }
}
