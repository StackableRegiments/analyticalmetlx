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

class MeTLQuizSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLDataGenerators with MeTLQuizMatchers {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract metl quiz from xml with four options") {

		val content = <message>
						<quiz>
						  <author>eecrole</author>
						  <identity>metlQuiz</identity>
						  <!-- quiz specific information -->
						  <question>How many planets are there in our solar system?</question>
						  <url>http://test.metl.com/test/quizImage00.png</url>
						  <id>eecrole:023409234923</id>
						  <isDeleted>false</isDeleted>
						  <quizOption>
							<name>A</name>
							<text>9 (including the sun)</text>
							<correct>true</correct>
							<color>#ffffff</color>
						  </quizOption>
						  <quizOption>
							<name>B</name>
							<text>8</text>
							<correct>false</correct>
							<color>#ffffff</color>
						  </quizOption>
						  <quizOption>
							<name>C</name>
							<text>11</text>
							<correct>false</correct>
							<color>#ffffff</color>
						  </quizOption>
						  <quizOption>
							<name>D</name>
							<text>7</text>
							<correct>false</correct>
							<color>#ffffff</color>
						  </quizOption>
						</quiz>
					  </message>
		// mercury, venus, earth, mars, jupiter, saturn, uranus, neptune
		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLQuiz]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			id ("eecrole:023409234923"),
			timestamp (-1L),
			isDeleted (false),
			question ("How many planets are there in our solar system?"),
			url (Full("http://test.metl.com/test/quizImage00.png")),
			imageBytes (Full(Array.empty[Byte])),
			options (List(
			  QuizOption("A", "9 (including the sun)", true, Color(0xff, 0xff, 0xff,0xff)), 
			  QuizOption("B", "8", false, Color(0xff, 0xff, 0xff,0xff)), 
			  QuizOption("C", "11", false, Color(0xff, 0xff, 0xff,0xff)), 
			  QuizOption("D", "7", false, Color(0xff, 0xff, 0xff,0xff))))
		)
	}

	test("extract metl quiz from xml with title instead of question") {

		val content = <message>
						<quiz>
						  <author>eecrole</author>
						  <identity>metlQuiz</identity>
						  <!-- quiz specific information -->
						  <title>Hello?</title>
						  <id>eecrole:023409234923</id>
						  <url></url>
						  <isDeleted>false</isDeleted>
						  <quizOption>
							<name>A</name>
							<text>Is it me you're looking for?</text>
							<correct>true</correct>
							<color>#ffffff</color>
						  </quizOption>
						</quiz>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLQuiz]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			timestamp (-1L),
			id ("eecrole:023409234923"),
			url (Empty),
			isDeleted (false),
			question ("Hello?"),
			imageBytes (Empty),
			options (List(QuizOption("A", "Is it me you're looking for?", true, Color(0xff, 0xff, 0xff,0xff))))
		)
	}

    test("serialise quiz to xml") {
        forAll (genQuiz) { (genQuiz: MeTLQuiz) =>
            
            implicit val xml = xmlSerializer.fromMeTLQuiz(genQuiz)

            genQuiz should have(
               author (queryXml[String]("author")),
               created (queryXml[Long]("created")),
               question (queryXml[String]("question")),
               id (queryXml[String]("id")),
               isDeleted (queryXml[Boolean]("isDeleted")),
               url (Full(queryXml[String]("url"))),
               options (xmlSerializer.getXmlByName(xml, "quizOption").map(qo => xmlSerializer.toQuizOption(qo)).toList)
            )
        }
    }
}

class MeTLQuizReponseSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLDataGenerators with MeTLQuizResponseMatchers {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract metl quiz response from xml") {

		val content = <message>
						<quizResponse>
						  <author>eecrole</author>
						  <identity>metlQuiz</identity>
						  <!-- quiz reponse specific information -->
						  <id>eecrole:023409234923</id>
						  <answer>A</answer>
						  <answerer>eecrole</answerer>
						</quizResponse>
					  </message>

		val result = xmlSerializer.toMeTLData(content).asInstanceOf[MeTLQuizResponse]

		result should have (
			server (ServerConfiguration.empty),
			author ("eecrole"),
			id ("eecrole:023409234923"),
			timestamp (-1L),
			answer ("A"),
			answerer ("eecrole")
		)
	}

    test("serialise quiz response to xml") {
        forAll (genQuizResponse) { (genQuizResponse: MeTLQuizResponse) =>
            
            implicit val xml = xmlSerializer.fromMeTLQuizResponse(genQuizResponse)

            genQuizResponse should have(
               author (queryXml[String]("author")),
               answer (queryXml[String]("answer")),
               answerer (queryXml[String]("answerer")),
               id (queryXml[String]("id"))
            )
        }
    }
}
