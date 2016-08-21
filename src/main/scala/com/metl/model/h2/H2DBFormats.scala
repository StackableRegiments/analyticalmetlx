package com.metl.h2.dbformats

import net.liftweb.mapper._

import com.metl.data._
import net.liftweb.common._

object H2Constants{
  val maxStanza = 65565
  val author = 64
  val room = 64
  val target = 64
  val identity = 128
  val fullIdentity = 2048
  val color = 9
  val url = 2048
  val tag = 4196
  val privacy = 20
  val metlType = 64
  val unhandledType = 64
}

abstract class MappedMeTLString[A <: Mapper[A]](owner:A,length:Int) extends MappedPoliteString(owner,length){
}

trait H2MeTLIndexedString extends IndexedField[String]{
  def convertKey(in:String):Box[String] = Box.legacyNullTest(in)
  def convertKey(in:Int):Box[String] = Full(in.toString)
  def convertKey(in:Long):Box[String] = Full(in.toString)
  def convertKey(in:AnyRef):Box[String] = Box.legacyNullTest(in).map(_.toString)
  def makeKeyJDBCFriendly(in:String) = in
}

trait H2MeTLContent[C <:H2MeTLContent[C]] extends LongKeyedMapper[C] with IdPK{
  self: C =>
  object metlType extends MappedMeTLString[C](this,H2Constants.metlType)
  object room extends MappedMeTLString[C](this,H2Constants.room) with H2MeTLIndexedString
  object audiences extends MappedText[C](this)
}

trait H2MeTLStanza[C <: H2MeTLStanza[C]] extends H2MeTLContent[C]{
  self: C =>
  object timestamp extends MappedLong[C](this)
  object author extends MappedMeTLString[C](this,H2Constants.author) with H2MeTLIndexedString
}

trait H2MeTLCanvasContent[C <: H2MeTLCanvasContent[C]] extends H2MeTLStanza[C] {
  self: C =>
  object target extends MappedMeTLString[C](this,H2Constants.target)
  object privacy extends MappedMeTLString[C](this,H2Constants.privacy)
  object slide extends MappedMeTLString[C](this,H2Constants.room)
  object identity extends MappedMeTLString[C](this,H2Constants.fullIdentity)
}

trait H2MeTLUnhandled[C <: H2MeTLUnhandled[C]] extends H2MeTLContent[C]{
  self: C =>
  object valueType extends MappedMeTLString[C](this,H2Constants.unhandledType)
  object unhandled extends MappedText[C](this)
}

class H2Attendance extends H2MeTLStanza[H2Attendance]{
  def getSingleton = H2Attendance
  object location extends MappedMeTLString(this,4096)
  object present extends MappedBoolean(this)
}
object H2Attendance extends H2Attendance with LongKeyedMetaMapper[H2Attendance] {
}
class H2UnhandledCanvasContent extends H2MeTLCanvasContent[H2UnhandledCanvasContent] with H2MeTLUnhandled[H2UnhandledCanvasContent] {
  def getSingleton = H2UnhandledCanvasContent
}
object H2UnhandledCanvasContent extends H2UnhandledCanvasContent with LongKeyedMetaMapper[H2UnhandledCanvasContent] {
}
class H2UnhandledStanza extends H2MeTLStanza[H2UnhandledStanza] with H2MeTLUnhandled[H2UnhandledStanza] {
  def getSingleton = H2UnhandledStanza
}
object H2UnhandledStanza extends H2UnhandledStanza with LongKeyedMetaMapper[H2UnhandledStanza] {
}
class H2UnhandledContent extends H2MeTLContent[H2UnhandledContent] with H2MeTLUnhandled[H2UnhandledContent] {
  def getSingleton = H2UnhandledContent
}
object H2UnhandledContent extends H2UnhandledContent with LongKeyedMetaMapper[H2UnhandledContent] {
}

class H2Ink extends H2MeTLCanvasContent[H2Ink] {
  def getSingleton = H2Ink
  object checksum extends MappedDouble(this)
  object startingSum extends MappedDouble(this)
  object points extends MappedText(this)
  object color extends MappedMeTLString(this,H2Constants.color)
  object thickness extends MappedDouble(this)
  object isHighlighter extends MappedBoolean(this)
}
object H2Ink extends H2Ink with LongKeyedMetaMapper[H2Ink] {
}
class H2MultiWordText extends H2MeTLCanvasContent[H2MultiWordText]{
  def getSingleton = H2MultiWordText
  object words extends MappedText(this)
  object x extends MappedDouble(this)
  object y extends MappedDouble(this)
  object width extends MappedDouble(this)
  object height extends MappedDouble(this)
  object requestedWidth extends MappedDouble(this)
  object tag extends MappedMeTLString(this,H2Constants.tag)
}
object H2MultiWordText extends H2MultiWordText with LongKeyedMetaMapper[H2MultiWordText]{
}

class H2Text extends H2MeTLCanvasContent[H2Text] {
  def getSingleton = H2Text
  object text extends MappedText(this)
  object height extends MappedDouble(this)
  object width extends MappedDouble(this)
  object caret extends MappedInt(this)
  object x extends MappedDouble(this)
  object y extends MappedDouble(this)
  object tag extends MappedMeTLString(this,H2Constants.tag)
  object style extends MappedMeTLString(this,64)
  object family extends MappedMeTLString(this,256)
  object weight extends MappedMeTLString(this,64)
  object size extends MappedDouble(this)
  object decoration extends MappedMeTLString(this,64)
  object color extends MappedMeTLString(this,H2Constants.color)
}
object H2Text extends H2Text with LongKeyedMetaMapper[H2Text] {
}
class H2Image extends H2MeTLCanvasContent[H2Image] {
  def getSingleton = H2Image
  object tag extends MappedMeTLString(this,H2Constants.tag)
  object source extends MappedMeTLString(this,H2Constants.url)
  object width extends MappedDouble(this)
  object height extends MappedDouble(this)
  object x extends MappedDouble(this)
  object y extends MappedDouble(this)
}
object H2Image extends H2Image with LongKeyedMetaMapper[H2Image] {
}
class H2DirtyInk extends H2MeTLCanvasContent[H2DirtyInk] {
  def getSingleton = H2DirtyInk
}
object H2DirtyInk extends H2DirtyInk with LongKeyedMetaMapper[H2DirtyInk]{
}
class H2DirtyText extends H2MeTLCanvasContent[H2DirtyText] {
  def getSingleton = H2DirtyText
}
object H2DirtyText extends H2DirtyText with LongKeyedMetaMapper[H2DirtyText]{
}
class H2DirtyImage extends H2MeTLCanvasContent[H2DirtyImage] {
  def getSingleton = H2DirtyImage
}
object H2DirtyImage extends H2DirtyImage with LongKeyedMetaMapper[H2DirtyImage]{
}
class H2MoveDelta extends H2MeTLCanvasContent[H2MoveDelta]{
  def getSingleton = H2MoveDelta
  object inkIds extends MappedText(this)
  object textIds extends MappedText(this)
  object multiWordTextIds extends MappedText(this)
  object imageIds extends MappedText(this)
  object xOrigin extends MappedDouble(this)
  object yOrigin extends MappedDouble(this)
  object xTranslate extends MappedDouble(this)
  object yTranslate extends MappedDouble(this)
  object xScale extends MappedDouble(this)
  object yScale extends MappedDouble(this)
  object newPrivacy extends MappedMeTLString(this,H2Constants.privacy)
  object isDeleted extends MappedBoolean(this)
}
object H2MoveDelta extends H2MoveDelta with LongKeyedMetaMapper[H2MoveDelta]{
}
class H2Quiz extends H2MeTLStanza[H2Quiz]{
  def getSingleton = H2Quiz
  object created extends MappedLong(this)
  object question extends MappedText(this)
  object quizId extends MappedMeTLString(this,H2Constants.fullIdentity)
  object url extends MappedMeTLString(this,H2Constants.url)
  object isDeleted extends MappedBoolean(this)
  object options extends MappedText(this)
}
object H2Quiz extends H2Quiz with LongKeyedMetaMapper[H2Quiz]{
}
class H2QuizResponse extends H2MeTLStanza[H2QuizResponse]{
  def getSingleton = H2QuizResponse
  object answer extends MappedMeTLString(this,8)
  object answerer extends MappedMeTLString(this,H2Constants.author)
  object quizId extends MappedMeTLString(this,H2Constants.fullIdentity)
}
object H2QuizResponse extends H2QuizResponse with LongKeyedMetaMapper[H2QuizResponse]{
}
class H2Command extends H2MeTLStanza[H2Command]{
  def getSingleton = H2Command
  object command extends MappedMeTLString(this,128)
  object commandParameters extends MappedText(this)
}
object H2Command extends H2Command with LongKeyedMetaMapper[H2Command]{
}
class H2Submission extends H2MeTLCanvasContent[H2Submission]{
  def getSingleton = H2Submission
  object title extends MappedMeTLString(this,512)
  object slideJid extends MappedInt(this)
  object url extends MappedMeTLString(this,H2Constants.url)
  object blacklist extends MappedText(this)
}
object H2Submission extends H2Submission with LongKeyedMetaMapper[H2Submission]{
}
class H2Conversation extends H2MeTLContent[H2Conversation]{
  def getSingleton = H2Conversation
  object author extends MappedMeTLString(this,H2Constants.author)
  object lastAccessed extends MappedLong(this)
  object subject extends MappedMeTLString(this,64)
  object tag extends MappedMeTLString(this,H2Constants.tag)
  object jid extends MappedInt(this)
  object title extends MappedMeTLString(this,512)
  object created extends MappedMeTLString(this,64)
  object creation extends MappedLong(this)
  object permissions extends MappedMeTLString(this,256)
  object blackList extends MappedText(this)
  object slides extends MappedText(this)
}
object H2Conversation extends H2Conversation with LongKeyedMetaMapper[H2Conversation]{
}
class H2File extends H2MeTLStanza[H2File]{
  def getSingleton = H2File
  object name extends MappedText(this)
  object partialIdentity extends MappedMeTLString(this,H2Constants.identity) with H2MeTLIndexedString
  object identity extends MappedMeTLString(this,H2Constants.fullIdentity)
  object url extends MappedMeTLString(this,H2Constants.url)
  object deleted extends MappedBoolean(this)
}
object H2File extends H2File with LongKeyedMetaMapper[H2File]{
}

class H2Resource extends H2MeTLContent[H2Resource]{
  def getSingleton = H2Resource
  object partialIdentity extends MappedMeTLString(this,H2Constants.identity) with H2MeTLIndexedString
  object identity extends MappedMeTLString(this,H2Constants.fullIdentity){
    override def dbColumnName = "url"
  }
  object bytes extends MappedBinary(this)
}
object H2Resource extends H2Resource with LongKeyedMetaMapper[H2Resource]{
}

class H2ContextualizedResource extends KeyedMapper[String,H2ContextualizedResource] {
  def getSingleton = H2ContextualizedResource
  def primaryKeyField = identity
  object context extends MappedMeTLString(this,H2Constants.room) with H2MeTLIndexedString
  object identity extends MappedStringIndex(this,H2Constants.identity){
    override def dbNotNull_? = true
    override def dbPrimaryKey_? = true
    override def dbIndexed_? = true
    override def dbAutogenerated_? = true
    //    override def writePermission_? = false // I think this should already be done.
  }
  object bytes extends MappedBinary(this)
}
object H2ContextualizedResource extends H2ContextualizedResource with KeyedMetaMapper[String,H2ContextualizedResource]{
}

object DatabaseVersion extends DatabaseVersion with LongKeyedMetaMapper[DatabaseVersion]{
}
class DatabaseVersion extends LongKeyedMapper[DatabaseVersion] with IdPK {
  def getSingleton = DatabaseVersion
  object key extends MappedPoliteString(this,1024)
  object scope extends MappedPoliteString(this,1024)
  object stringValue extends MappedText(this)
  object intValue extends MappedInt(this){
    override def defaultValue = -1
  }
}
