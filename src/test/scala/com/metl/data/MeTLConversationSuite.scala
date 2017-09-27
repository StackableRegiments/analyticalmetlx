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

class MeTLConversationSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with ShouldMatchers with QueryXml with MeTLDataGenerators with ConversationMatchers {

	var xmlSerializer: GenericXmlSerializer = _

	before {
	  xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
	}

	test("extract conversation from xml") {

		val content = 
      <message>
        <conversation>
          <author>eecrole</author>
          <lastModified>234234234234</lastModified>    
          <slides>
              <slide>
                  <author>eecrole</author>
                  <id>3453463</id>
                  <index>0</index>
                  <created>23524634634343</created>
                  <modified>23524634634344</modified>
                  <exposed>true</exposed>
                  <type>SLIDE</type>
              </slide>
              <slide>
                  <author>eecrole</author>
                  <id>3453464</id>
                  <index>1</index>
                  <created>23524634634343</created>
                  <modified>23524634634344</modified>
                  <exposed>false</exposed>
                  <type>SLIDE</type>
              </slide>
              <slide>
                  <author>eecrole</author>
                  <id>3453465</id>
                  <index>2</index>
                  <created>23524634634343</created>
                  <modified>23524634634344</modified>
                  <exposed>true</exposed>
                  <type>SLIDE</type>
              </slide>
          </slides>
          <jid>232523454</jid>
          <title>The quest for answers</title>
          <creation>23524634634343</creation>
          <permissions>
            <structurePermission>
              <canView>
                <everyoneCanAccess />
              </canView>
              <canInteract>
                <noOneCanAccess />
              </canInteract>
              <canAdminister>
                <accountAccess name="a" provider="b"/>
              </canAdminister>
            </structurePermission>
          </permissions>
        </conversation>
      </message>

		val result = xmlSerializer.toConversation(content)

    val creation = 23524634634343L
    val modification = 23524634634344L
		result should have (
			author ("eecrole"),
			lastModified (234234234234L),
      jid ("232523454"),
      title ("The quest for answers"),
      created (creation),
      slides (List(
        Slide(author = "eecrole", id = "3453463", index = 0, created = creation, modified = modification, exposed = true),
        Slide(author = "eecrole", id = "3453464", index = 1, created = creation, modified = modification, exposed = false),
        Slide(author = "eecrole", id = "3453465", index = 2, created = creation, modified = modification, exposed = true))
      ),
      permissions (StructurePermission(
        EveryoneCanAccess,
        NoOneCanAccess,
        AccountAccessControl("a","b")
      ))
		)
	}

    test("serialise conversation to xml") {
        forAll (genConversation) { (genConversation: Conversation) =>
            
            implicit val xml = xmlSerializer.fromConversation(genConversation)
            genConversation should have(
               author (queryXml[String]("author")),
               lastModified (queryXml[Long]("lastModified")),
               jid (queryXml[String]("jid")),
               title (queryXml[String]("title")),
               created (queryXml[Long]("creation")),
               slides (xmlSerializer.getXmlByName(xml, "slide").map(s => xmlSerializer.toSlide(s)).toList),
               permissions ((xml \ "permissions").headOption.map(p => xmlSerializer.toStructurePermission(p)).getOrElse(StructurePermission.empty))
            )
        }
    }
}
