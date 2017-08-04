package com.metl.data

import scala.xml._
import org.scalatest.{Matchers,FunSuite,BeforeAndAfter,_}
import org.scalatest.matchers.{HavePropertyMatcher, HavePropertyMatchResult}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.OptionValues._
import org.scalatest.StreamlinedXmlNormMethods._
import org.scalactic._
import TripleEquals._

import net.liftweb.util.Helpers._
import net.liftweb.common._
import scala.xml._
import com.metl.data._
import com.metl.model._
import Privacy._

class GenericXmlSerializerSuite extends FunSuite with GeneratorDrivenPropertyChecks with BeforeAndAfter with Matchers with QueryXml with MeTLStanzaMatchers with MeTLCanvasContentMatchers with MeTLDataGenerators {
  var xmlSerializer: GenericXmlSerializer = _
  object XmlUtils extends XmlUtils

  before {
    xmlSerializer = new GenericXmlSerializer(EmptyBackendAdaptor)
  }

  test("extract privacy of private from content") {
    val content = <ink><color>blue</color><privacy>private</privacy></ink>
    val result = XmlUtils.getPrivacyByName(content, "privacy")

    assert(result === Privacy.PRIVATE)
  }

  test("extract privacy of not_set from content") {
    val content = <ink><color>blue</color><privacy>not_set</privacy></ink>
    val result = XmlUtils.getPrivacyByName(content, "privacy")

    assert(result === Privacy.NOT_SET)
  }

  test("extract privacy of public from content") {
    val content = <ink><color>blue</color><privacy>public</privacy></ink>
    val result = XmlUtils.getPrivacyByName(content, "privacy")

    assert(result === Privacy.PUBLIC)
  }

  test("extract non-existent privacy from content") {
    val content = <ink><color>blue</color></ink>
    val result = XmlUtils.getPrivacyByName(content, "privacy")

    assert(result === Privacy.NOT_SET)
  }

  test("extract invalid privacy value from privacy element") {
    val content = <ink><color>blue</color><privacy>brown</privacy></ink>
    val result = XmlUtils.getPrivacyByName(content, "privacy")

    assert(result === Privacy.NOT_SET)
  }

  test("extract color as hexstring from specified element name") {
    val content = <ink><color>#0fafbfcf</color><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color(0x0f, 0xaf, 0xbf, 0xcf))
  }

  test("extract color as long hexstring from specified element name") {
    val content = <ink><color>#0fafbfcf7b</color><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color.default)
  }

  test("extract color as 3 numbers from content") {
    val content = <ink><blah><color>240 250 128</color></blah><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color(0xff, 240, 250, 128))
  }

  test("extract color as 4 numbers from content") {
    val content = <ink><blah><color>240 250 128 120</color></blah><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color(120, 240, 250, 128))
  }

  test("extract non-existent color element from content") {
    val content = <ink><elephant>african</elephant><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color.default)
  }

  test("extract invalid color from content") {
    val content = <ink><color>mango</color><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color.default)
  }

  test("extract 4 number color outside of range") {
    val content = <ink><blah><color>240 400 128 120</color></blah><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color(120, 240, 255, 128))
  }

  test("extract 3 number color outside of range") {
    val content = <ink><blah><color>240 400 128</color></blah><privacy>private</privacy></ink>
    val result = XmlUtils.getColorByName(content, "color")

    assert(result === Color(255, 240, 255, 128))
  }

  test("extract string value from specified element") {
    val content = <ink><elephant>african</elephant><privacy>private</privacy></ink>
    val result = XmlUtils.getStringByName(content, "elephant")

    assert(result === "african")
  }

  test("extract string value from non-existent specified element") {
    val content = <ink><elephant>african</elephant><privacy>private</privacy></ink>
    val result = XmlUtils.getStringByName(content, "tree")

    assert(result === "")
  }

  test("extract true boolean value from specified element") {
    val content = <ink><isHighlighter>true</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === true)
  }

  test("extract TRUE boolean value from specified element") {
    val content = <ink><isHighlighter>TRUE</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === true)
  }

  test("extract TrUe boolean value from specified element") {
    val content = <ink><isHighlighter>TrUe</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === true)
  }

  test("extract false boolean value from specified element") {
    val content = <ink><isHighlighter>false</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === false)
  }

  test("extract FALSE boolean value from specified element") {
    val content = <ink><isHighlighter>FALSE</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === false)
  }

  test("extract invalid boolean value from specified element") {
    val content = <ink><isHighlighter>termites</isHighlighter><privacy>private</privacy></ink>
    val result = XmlUtils.getBooleanByName(content, "isHighlighter")

    assert(result === false)
  }

  test("extract negative double value from specified element") {
    val content = <ink><coordX>-123434.02345</coordX></ink>
    val result = XmlUtils.getDoubleByName(content, "coordX")

    assert(result === -123434.02345)
  }

  test("extract positive double value from specified element") {
    val content = <ink><coordX>434.045</coordX></ink>
    val result = XmlUtils.getDoubleByName(content, "coordX")

    assert(result === 434.045)
  }

  test("extract invalid double value from specified element") {
    val content = <ink><coordX>pants</coordX></ink>
    val result = XmlUtils.getDoubleByName(content, "coordX")

    assert(result === -1D)
  }

  test("extract negative long value from specified element") {
    val content = <ink><timestamp>-142345224502350203</timestamp></ink>
    val result = XmlUtils.getLongByName(content, "timestamp")

    assert(result === -142345224502350203L)
  }

  test("extract positive long value from specified element") {
    val content = <ink><timestamp>92387434597823495</timestamp></ink>
    val result = XmlUtils.getLongByName(content, "timestamp")

    assert(result === 92387434597823495L)
  }

  test("extract invalid long value from specified element") {
    val content = <ink><timestamp>pants</timestamp></ink>
    val result = XmlUtils.getLongByName(content, "timestamp")

    assert(result === -1L)
  }

  test("extract negative int value from specified element") {
    val content = <ink><id>-1234235</id></ink>
    val result = XmlUtils.getIntByName(content, "id")

    assert(result === -1234235)
  }

  test("extract positive int value from specified element") {
    val content = <ink><id>345377</id></ink>
    val result = XmlUtils.getIntByName(content, "id")

    assert(result === 345377)
  }

  test("extract invalid int value from specified element") {
    val content = <ink><id>pants</id></ink>
    val result = XmlUtils.getIntByName(content, "id")

    assert(result === -1)
  }

  test("extract list of strings by name within container") {
    val content = <ink>
    <strokes>
    <strokeId>1</strokeId>
    <strokeId>2</strokeId>
    <strokeId>3</strokeId>
    <strokeId>4</strokeId>
    <strokeId>5</strokeId>
    </strokes>
    </ink>

    val result = XmlUtils.getListOfStringsByNameWithin(content, "strokeId", "strokes")

    assert(result === List("1", "2", "3", "4", "5"))
  }

  test("extract embedded author and message stanza") {

    val content =       <message>
    <author>eecrole</author>
    <metlMetaData>
    <timestamp>3453463456234</timestamp>
    </metlMetaData>
    </message>

    val result = xmlSerializer.toMeTLUnhandledStanza(content)

    result should have (
      server (ServerConfiguration.empty),
      author ("eecrole"),
      timestamp (3453463456234L)
    )
  }

  test("extract value of element by name") {
    val content = <ink><coordX>pants</coordX></ink>
    val result = XmlUtils.getValueOfNode(content, "coordX")

    assert(result === "pants")
  }

  test("extract value of element by name returns only first value") {
    val content = <ink><coordX>pants</coordX><coordX>hats</coordX><coordX>shirts</coordX></ink>
    val result = XmlUtils.getValueOfNode(content, "coordX")

    assert(result === "pants")
  }

  test("extract xml by name") {
    val content = <ink><coordX>pants</coordX></ink>
    val result = XmlUtils.getXmlByName(content, "coordX")

    assert(result.toString === <coordX>pants</coordX>.toString)
  }

  test("extract xml deep by name") {
    val content = <ink><id>2345</id><attributes><coordX>345</coordX><isHighlighter>false</isHighlighter><color>blue</color></attributes></ink>
    val result = XmlUtils.getXmlByName(content, "attributes")

    assert(result.toString === <attributes><coordX>345</coordX><isHighlighter>false</isHighlighter><color>blue</color></attributes>.toString)
  }

  test("extract non-existent xml by name") {
    val content = <something>some value</something>
    val result = XmlUtils.getXmlByName(content, "cat")

    result should be(NodeSeq.Empty)
  }

  test("extract attribute of node") {
    val content = <ink><color tip="true">black</color></ink>
    val result = XmlUtils.getAttributeOfNode(content, "color", "tip")

    result should equal("true")
  }

  test("extract non-existent attribute of node") {
    val content = <ink><color alpha="50%">black</color></ink>
    val result = XmlUtils.getAttributeOfNode(content, "color", "tip")

    assert(result === "")
  }

  test("extract canvas content") {
    val content = <content>
    <target>presentationSpace</target>
    <privacy>Private</privacy>
    <slide>3</slide>
    <identity>eecrole</identity>
    </content>

    val result = XmlUtils.parseCanvasContent(content)

    assert(result === ParsedCanvasContent("presentationSpace", Privacy.PRIVATE, "3", "eecrole"))
  }

  test("canvas content to xml") {

    val content = ParsedCanvasContent("presentationSpace", Privacy.PRIVATE, "3", "eecrole")

    val result = XmlUtils.parsedCanvasContentToXml(content)

    result should equal(<target>presentationSpace</target><privacy>private</privacy><slide>3</slide><identity>eecrole</identity>)
  }

  test("metl content to xml") {

    val content = ParsedMeTLContent("eecrole", -1L,Nil)

    val result = XmlUtils.parsedMeTLContentToXml(content)

    result should equal(<author>eecrole</author><audiences></audiences>)
  }

  test("metl content to xml with audiences") {
    val content = ParsedMeTLContent("eecrole", -1L,List(Audience(ServerConfiguration.empty,"a","b","c","d"),Audience(ServerConfiguration.empty,"e","f","g","h")))

    val result = XmlUtils.parsedMeTLContentToXml(content)

    result should equal(<author>eecrole</author><audiences><audience domain="a" name="b" type="c" action="d"/><audience domain="e" name="f" type="g" action="h"/></audiences>)
  }

  test("theme to xml") {
    val content = MeTLTheme(ServerConfiguration.empty,"anauthor",1,"2",Theme("anauthor","sometext","anorigin"),Nil)
    val result = xmlSerializer.fromTheme(content).norm.toString
    assert((<message timestamp="1"><theme>
      <author>anauthor</author>
      <audiences />
      <text>sometext</text>
      <origin>anorigin</origin>
      <location>2</location>
      </theme></message>).norm.toString === result)
  }
  test("parse theme"){
    val content = <theme>
    <author>anauthor</author>
    <location>2</location>
    <metlMetaData><timestamp>1</timestamp></metlMetaData>
    <text>sometext</text>
    <origin>anorigin</origin>
    <audiences />
    </theme>
    val result = xmlSerializer.toTheme(content)
    assert(result === MeTLTheme(ServerConfiguration.empty,"anauthor",1,"2",Theme("anauthor","sometext","anorigin"),Nil))
  }

  test("extract different depth canvas content") {
    val content = <conversation>
    <canvas render="main">
    <content>
    Lots of content
    </content>
    </canvas>
    <canvas render="notepad">
    <identity>eecrole</identity>
    <target>presentationSpace</target>
    <privacy>Private</privacy>
    <slides>
    <slide>3</slide>
    </slides>
    </canvas>
    <metadata>
    <timestamp>334534</timestamp>
    </metadata>
    </conversation>

    val result = XmlUtils.parseCanvasContent(content)

    assert(result === ParsedCanvasContent("presentationSpace", Privacy.PRIVATE, "3", "eecrole"))
  }

  test("deconstruct parsed canvas content to xml") {
    val parsed = ParsedCanvasContent("target", Privacy.PUBLIC, "12", "eecrole")

    val result = XmlUtils.parsedCanvasContentToXml(parsed)

    result should equal(<target>target</target><privacy>public</privacy><slide>12</slide><identity>eecrole</identity>)
  }

  test("extract metl content from xml") {
    val content = <metldata><author>eecrole</author><metlmetadata><timestamp>234234534634</timestamp></metlmetadata></metldata>

    val result = XmlUtils.parseMeTLContent(content)
    info("timestamp is ignored")
    assert(result === ParsedMeTLContent("eecrole", -1L,Nil))
  }

  test("deconstruct metl content to xml") {
    val parsed = ParsedMeTLContent("eecrole", 235245290623L,Nil)
    val result = XmlUtils.parsedMeTLContentToXml(parsed)

    info("timestamp is ignored")
    result should equal(<author>eecrole</author><audiences></audiences>)
  }

  test("old metl data successfully gets timestamp") {
    val message = <message xml:lang='en' to='5683475@conference.reifier.adm.monash.edu.au' from='hren12@reifier.adm.monash.edu.au/634667262786134810' type='groupchat' time='1331095596928'><ink xmlns='monash:metl'><checksum>43301.7</checksum><startingSum>43301.7</startingSum><points>661.6 42.7 3 660.9 43.9 9 660.2 45.4 15 659.4 47 22 658.7 48.8 26 657.9 50.9 30 657.3 53.1 32 656.6 55.4 34 656.1 57.8 36 655.7 60 38 655.4 62.3 39 655.3 64.4 40 655.5 66.4 42 655.8 68.3 44 656.4 69.9 46 657.4 71.4 48 658.5 72.6 49 659.8 73.7 51 661.4 74.4 52 663 74.9 53 664.8 75.2 55 666.5 75.1 58 668.2 74.8 62 669.8 74.1 67 671.3 73.2 72 672.7 71.9 78 674.1 70.6 85 675.2 68.7 91 676.3 66.8 96 677.3 64.7 100 678.2 62.6 102 678.8 60.5 104 679.4 58.5 106 679.7 56.5 108 679.9 54.8 110 679.9 53.2 112 679.7 51.8 114 679.1 50.5 117 678.6 49.4 120 677.6 48.2 123 676.7 47.3 127 675.4 46.3 130 674 45.7 132 672.3 44.9 135 670.5 44.5 137 668.5 44.1 138 666.7 43.9 139 664.8 43.8 139 663.1 43.8 139 661.5 43.8 139 660.2 44 139 659.1 44.1 139 658.3 44.3 138 657.7 44.5 137 657.7 44.5 134 657.2 44.9 127 657.2 44.9 109 657.2 44.9 77 657.2 44.9 45 657.7 45.2 13</points><color>0 0 255 255</color><thickness>1.96243702401875</thickness><highlight>False</highlight><author>hren12</author><target>presentationSpace</target><privacy>public</privacy><slide>5683475</slide></ink></message>
    val stanza = xmlSerializer.toMeTLInk(message)
    assert(stanza.timestamp == 1331095596928L)
  }

  test("new metl data successfully gets timestamp") {
    val message = <message id='I4TvF-709' to='5683475@conference.metl.adm.monash.edu.au' type='groupchat'><ink xmlns='monash:metl'><author>chagan</author><target>presentationSpace</target><privacy>public</privacy><slide>5683475</slide><identity>21758.0</identity><checksum>21758.0</checksum><startingSum>21758.0</startingSum><points>494.0 168.0 128.0 494.0 167.0 128.0 494.0 166.0 128.0 490.0 165.0 128.0 486.0 163.0 128.0 483.0 158.0 128.0 480.0 155.0 128.0 478.0 149.0 128.0 478.0 147.0 128.0 478.0 144.0 128.0 478.0 142.0 128.0 479.0 139.0 128.0 480.0 138.0 128.0 490.0 136.0 128.0 494.0 136.0 128.0 502.0 136.0 128.0 503.0 136.0 128.0 503.0 142.0 128.0 503.0 146.0 128.0 498.0 165.0 128.0 494.0 174.0 128.0 491.0 181.0 128.0 490.0 183.0 128.0 486.0 188.0 128.0 486.0 190.0 128.0 485.0 191.0 128.0 484.0 192.0 128.0 484.0 192.0 128.0</points><color>0 0 0 255</color><thickness>2.0</thickness><highlight>false</highlight></ink><metlMetaData><timestamp>1378820229355</timestamp><conn>c2s_tls</conn><ip>130.194.20.186</ip><port>45816</port><node>ejabberd@localhost</node></metlMetaData></message>
    val stanza = xmlSerializer.toMeTLInk(message)
    assert(stanza.timestamp == 1378820229355L)
  }
  test("old metl ink successfully gets identity") {
    val message = <message xml:lang='en' to='5683475@conference.reifier.adm.monash.edu.au' from='hren12@reifier.adm.monash.edu.au/634667262786134810' type='groupchat' time='1331095596928'><ink xmlns='monash:metl'><checksum>43301.7</checksum><startingSum>43301.7</startingSum><points>661.6 42.7 3 660.9 43.9 9 660.2 45.4 15 659.4 47 22 658.7 48.8 26 657.9 50.9 30 657.3 53.1 32 656.6 55.4 34 656.1 57.8 36 655.7 60 38 655.4 62.3 39 655.3 64.4 40 655.5 66.4 42 655.8 68.3 44 656.4 69.9 46 657.4 71.4 48 658.5 72.6 49 659.8 73.7 51 661.4 74.4 52 663 74.9 53 664.8 75.2 55 666.5 75.1 58 668.2 74.8 62 669.8 74.1 67 671.3 73.2 72 672.7 71.9 78 674.1 70.6 85 675.2 68.7 91 676.3 66.8 96 677.3 64.7 100 678.2 62.6 102 678.8 60.5 104 679.4 58.5 106 679.7 56.5 108 679.9 54.8 110 679.9 53.2 112 679.7 51.8 114 679.1 50.5 117 678.6 49.4 120 677.6 48.2 123 676.7 47.3 127 675.4 46.3 130 674 45.7 132 672.3 44.9 135 670.5 44.5 137 668.5 44.1 138 666.7 43.9 139 664.8 43.8 139 663.1 43.8 139 661.5 43.8 139 660.2 44 139 659.1 44.1 139 658.3 44.3 138 657.7 44.5 137 657.7 44.5 134 657.2 44.9 127 657.2 44.9 109 657.2 44.9 77 657.2 44.9 45 657.7 45.2 13</points><color>0 0 255 255</color><thickness>1.96243702401875</thickness><highlight>False</highlight><author>hren12</author><target>presentationSpace</target><privacy>public</privacy><slide>5683475</slide></ink></message>
    val stanza = xmlSerializer.toMeTLInk(message)
    assert(stanza.identity == "43301.7")
  }
  test("new metl ink successfully gets identity") {
    val message = <message id='I4TvF-709' to='5683475@conference.metl.adm.monash.edu.au' type='groupchat'><ink xmlns='monash:metl'><author>chagan</author><target>presentationSpace</target><privacy>public</privacy><slide>5683475</slide><identity>21758.0</identity><checksum>21758.0</checksum><startingSum>21758.0</startingSum><points>494.0 168.0 128.0 494.0 167.0 128.0 494.0 166.0 128.0 490.0 165.0 128.0 486.0 163.0 128.0 483.0 158.0 128.0 480.0 155.0 128.0 478.0 149.0 128.0 478.0 147.0 128.0 478.0 144.0 128.0 478.0 142.0 128.0 479.0 139.0 128.0 480.0 138.0 128.0 490.0 136.0 128.0 494.0 136.0 128.0 502.0 136.0 128.0 503.0 136.0 128.0 503.0 142.0 128.0 503.0 146.0 128.0 498.0 165.0 128.0 494.0 174.0 128.0 491.0 181.0 128.0 490.0 183.0 128.0 486.0 188.0 128.0 486.0 190.0 128.0 485.0 191.0 128.0 484.0 192.0 128.0 484.0 192.0 128.0</points><color>0 0 0 255</color><thickness>2.0</thickness><highlight>false</highlight></ink><metlMetaData><timestamp>1378820229355</timestamp><conn>c2s_tls</conn><ip>130.194.20.186</ip><port>45816</port><node>ejabberd@localhost</node></metlMetaData></message>
    val stanza = xmlSerializer.toMeTLInk(message)
    assert(stanza.identity == "21758.0")
  }

  test("construct generic xml serializer with empty server configuration") {

    assert(xmlSerializer.configName === "empty")
  }
  test("parse metl move delta to xml and back") {
    forAll (genMoveDelta) { (gennedMoveDelta: MeTLMoveDelta) =>

      val xml = xmlSerializer.fromMeTLMoveDelta(gennedMoveDelta)
      val md = xmlSerializer.toMeTLMoveDelta(xml)

      md should equal(gennedMoveDelta)
    }
  }

  test("parse metl ink to xml and back") {
    forAll (genInk) { (gennedInk: MeTLInk) =>

      val xml = xmlSerializer.fromMeTLInk(gennedInk)
      val ink = xmlSerializer.toMeTLInk(xml)

      ink should equal(gennedInk)
    }
  }

  test("parse metl image to xml and back") {
    forAll (genImage) { (gennedImage: MeTLImage) =>

      val xml = xmlSerializer.fromMeTLImage(gennedImage)
      val image = xmlSerializer.toMeTLImage(xml)
      //info("toMeTLImage: Creates Full(Array.empty[Byte]) instead of Empty, and Array instead of WrappedArray")

      image should equal(gennedImage)
    }
  }

  test("parse metl text to xml and back") {
    forAll (genText) { (gennedText: MeTLText) =>

      val xml = xmlSerializer.fromMeTLText(gennedText)
      val text = xmlSerializer.toMeTLText(xml)

      text should equal(gennedText)
    }
  }

  test("parse metl multi-word text to xml and back") {
    forAll (genMultiWordText) { (gennedText: MeTLMultiWordText) =>

      val xml = xmlSerializer.fromMeTLMultiWordText(gennedText)
      val text = xmlSerializer.toMeTLMultiWordText(xml)

      text should equal(gennedText)
    }
  }

  test("parse metl dirty ink to xml and back") {
    forAll (genDirtyInk) { (gennedDirtyInk: MeTLDirtyInk) =>

      val xml = xmlSerializer.fromMeTLDirtyInk(gennedDirtyInk)
      val dirtyInk = xmlSerializer.toMeTLDirtyInk(xml)

      dirtyInk should equal(gennedDirtyInk)
    }
  }

  test("parse metl dirty image to xml and back") {
    forAll (genDirtyImage) { (gennedDirtyImage: MeTLDirtyImage) =>

      val xml = xmlSerializer.fromMeTLDirtyImage(gennedDirtyImage)
      val dirtyImage = xmlSerializer.toMeTLDirtyImage(xml)

      dirtyImage should equal(gennedDirtyImage)
    }
  }

  test("parse metl dirty text to xml and back") {
    forAll (genDirtyText) { (gennedDirtyText: MeTLDirtyText) =>

      val xml = xmlSerializer.fromMeTLDirtyText(gennedDirtyText)
      val dirtyText = xmlSerializer.toMeTLDirtyText(xml)

      dirtyText should equal(gennedDirtyText)
    }
  }

  test("parse metl command to xml and back") {
    forAll (genCommand) { (gennedCommand: MeTLCommand) =>

      val xml = xmlSerializer.fromMeTLCommand(gennedCommand)
      val command = xmlSerializer.toMeTLCommand(xml)

      command should equal(gennedCommand)
    }
  }

  test("parse submission to xml and back") {
    forAll (genSubmission) { (gennedSubmission: MeTLSubmission) =>
      val xml = xmlSerializer.fromSubmission(gennedSubmission)
      val submission = xmlSerializer.toSubmission(xml)
      submission should equal(gennedSubmission)
    }
  }

  test("parse grade to xml and back") {
    forAll (genGrade) { (gennedStanza:MeTLGrade) => {
      val xml = xmlSerializer.fromGrade(gennedStanza)
      val stanza = xmlSerializer.toGrade(xml)
      stanza should equal(gennedStanza)
    }}
  }
  test("parse numericGradeValue to xml and back") {
    forAll (genNumericGradeValue) { (gennedStanza:MeTLNumericGradeValue) => {
      val xml = xmlSerializer.fromNumericGradeValue(gennedStanza)
      val stanza = xmlSerializer.toNumericGradeValue(xml)
      stanza should equal(gennedStanza)
    }}
  }
  test("parse booleanGradeValue to xml and back") {
    forAll (genBooleanGradeValue) { (gennedStanza:MeTLBooleanGradeValue) => {
      val xml = xmlSerializer.fromBooleanGradeValue(gennedStanza)
      val stanza = xmlSerializer.toBooleanGradeValue(xml)
      stanza should equal(gennedStanza)
    }}
  }
  test("parse textGradeValue to xml and back") {
    forAll (genTextGradeValue) { (gennedStanza:MeTLTextGradeValue) => {
      val xml = xmlSerializer.fromTextGradeValue(gennedStanza)
      val stanza = xmlSerializer.toTextGradeValue(xml)
      stanza should equal(gennedStanza)
    }}
  }
}
