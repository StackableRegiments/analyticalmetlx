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

		val content = <message>
                        <conversation>
						    <author>eecrole</author>
                            <lastAccessed>234234234234</lastAccessed>    
                            <slides>
                                <slide>
                                    <author>eecrole</author>
                                    <id>3453463</id>
                                    <index>0</index>
                                    <defaultHeight>540</defaultHeight>
                                    <defaultWidth>720</defaultWidth>
                                    <exposed>false</exposed>
                                    <type>SLIDE</type>
                                </slide>
                                <slide>
                                    <author>eecrole</author>
                                    <id>3453464</id>
                                    <index>1</index>
                                    <defaultHeight>540</defaultHeight>
                                    <defaultWidth>720</defaultWidth>
                                    <exposed>false</exposed>
                                    <type>SLIDE</type>
                                </slide>
                                <slide>
                                    <author>eecrole</author>
                                    <id>3453465</id>
                                    <index>2</index>
                                    <defaultHeight>540</defaultHeight>
                                    <defaultWidth>720</defaultWidth>
                                    <exposed>false</exposed>
                                    <type>SLIDE</type>
                                </slide>
                            </slides>
                            <subject>Why?</subject>
                            <tag>lelsdjfljksdf</tag>
                            <jid>232523454</jid>
                            <title>The quest for answers</title>
                            <creation>23524634634343</creation>
                            <permissions>
                                <studentsCanOpenFriends>false</studentsCanOpenFriends>
                                <studentCanPublish>false</studentCanPublish>
                                <usersAreCompulsorilySynced>true</usersAreCompulsorilySynced>
                                <studentsMayBroadcast>true</studentsMayBroadcast>
                                <studentsMayChatPublicly>true</studentsMayChatPublicly>
                            </permissions>
						</conversation>
					  </message>

		val result = xmlSerializer.toConversation(content)

		result should have (
			author ("eecrole"),
			lastAccessed (234234234234L),
            subject ("Why?"),
            tag ("lelsdjfljksdf"),
            jid (232523454),
            title ("The quest for answers"),
            created (23524634634343L),
            permissions(Permissions(ServerConfiguration.empty, false, false, true,true,true)),
            slides (List(
              Slide(ServerConfiguration.empty, "eecrole", 3453463, 0),
              Slide(ServerConfiguration.empty, "eecrole", 3453464, 1),
              Slide(ServerConfiguration.empty, "eecrole", 3453465, 2)))
		)
	}

    test("serialise conversation to xml") {
        forAll (genConversation) { (genConversation: Conversation) =>
            
            implicit val xml = xmlSerializer.fromConversation(genConversation)

            genConversation should have(
               author (queryXml[String]("author")),
               lastAccessed (queryXml[Long]("lastAccessed")),
               subject (queryXml[String]("subject")),
               tag (queryXml[String]("tag")),
               jid (queryXml[Int]("jid")),
               title (queryXml[String]("title")),
               created (queryXml[Long]("creation")),
               slides (xmlSerializer.getXmlByName(xml, "slide").map(s => xmlSerializer.toSlide(s)).toList),
               permissions (xmlSerializer.getXmlByName(xml, "permissions").map(p => xmlSerializer.toPermissions(p)).head)
            )
        }
    }
}
