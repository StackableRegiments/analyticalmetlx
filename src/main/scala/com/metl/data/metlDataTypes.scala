package com.metl.data

import com.metl.utils._
import com.metl.model._

import net.liftweb.common._
import net.liftweb.util.Helpers._
import java.util.Date
import com.metl.liftAuthenticator.ForeignRelationship

object PointConverter {
  def fromText(t:String):List[Point] = parsePoints(t.split(" ").toList)
  def toText(points:List[Point]):String = points.map(p => toText(p)).mkString(" ")
  def toText(point:Point):String = "%s %s %s".format(point.x,point.y,point.thickness)
  protected val pointCount = 3
  protected def constructPoint(pointElements:List[String]):Option[Point] = {
    try {
      Some(Point(pointElements(0).toDouble,pointElements(1).toDouble,pointElements(2).toDouble))
    } catch {
      case e:Exception => None
    }
  }
  protected def parsePoints(incomingPoints:List[String]):List[Point] = {
    incomingPoints.grouped(3).toList.flatMap(constructPoint _)
  }
}

object ColorConverter{
  def toHexString(c:Color) = toARGBHexString(c)
  def toRGBHexString(c:Color) = "#%02x%02x%02x".format(c.red,c.green,c.blue)
  def toARGBHexString(c:Color) = "#%02x%02x%02x%02x".format(c.alpha,c.red,c.green,c.blue)
  def toRGBAString(c:Color) = "%s %s %s %s".format(c.red,c.green,c.blue,c.alpha)
  def fromText:PartialFunction[String,Color] = {
    case s:String if (s.length == 9 && s.startsWith("#")) => fromHexString(s)
    case s:String if (s.split(" ").length == 3) => asSplit(s+ " 255")
    case s:String if (s.split(" ").length == 4) => asSplit(s)
    case s:String if (s.startsWith("Color(")) => {
      asSplit(s.drop("Color(".length).takeWhile(c => c != ')').split(',').drop(1).mkString(" ") + " 255")

    }
    case s => {
      Color.default
    }
  }
  def hexToInt(h:String):Int = tryo(Integer.parseInt(h,16)).openOr(0)
  def convert2AfterN(h:String,n:Int):Int = hexToInt(h.drop(n).take(2).mkString)
  def fromHexString(h:String):Color = fromARGBHexString(h)
  def fromRGBHexString(h:String):Color = {
    val r = convert2AfterN(h,1)
    val g = convert2AfterN(h,3)
    val b = convert2AfterN(h,5)
    Color(255,clamp(r),clamp(g),clamp(b))
  }
  def fromARGBHexString(h:String):Color = {
    val a = convert2AfterN(h,1)
    val r = convert2AfterN(h,3)
    val g = convert2AfterN(h,5)
    val b = convert2AfterN(h,7)
    Color(clamp(a),clamp(r),clamp(g),clamp(b))
  }
  private def asSplit(l:String):Color = {
    val parts = l.split(" ").map(_.toInt).toList
    val a = parts(3)
    val r = parts(0)
    val g = parts(1)
    val b = parts(2)
    Color(clamp(a),clamp(r),clamp(g),clamp(b))
  }
  private def clamp (n:Integer,min:Integer=0,max:Integer=255) = Math.max(min,Math.min(max,n))
}

object Privacy extends Enumeration{
  type Privacy = Value
  val PUBLIC, PRIVATE, NOT_SET = Value
  def parse(possiblePrivacy:String):Privacy = possiblePrivacy match {
    case s:String if s.toLowerCase == "public" => PUBLIC
    case s:String if s.toLowerCase == "private" => PRIVATE
    case s:String if s.toLowerCase == "not_set" => NOT_SET
    case _ => NOT_SET
  }
}
import Privacy._

case class Color(alpha:Int,red:Int,green:Int,blue:Int)
object Color{
  def empty = Color(0,0,0,0)
  def default:Color = Color(255,255,255,255)
}
case class Point(x:Double,y:Double,thickness:Double){
}
object Point{
  val empty = Point(0.0,0.0,0.0)
}

object Presentation{
  def empty = Presentation(Conversation.empty)
}

case class GroupSet(id:String,location:String,groupingStrategy:GroupingStrategy,groups:List[Group],override val audiences:List[Audience] = Nil) extends MeTLData(audiences){
  def contains(person:String) = groups.exists(_.members.contains(person))
}
object GroupSet {
  def empty = GroupSet("","",EveryoneInOneGroup,Nil,Nil)
}

abstract class GroupingStrategy extends Logger {
  def addNewPerson(g:GroupSet,person:String):GroupSet
}

case class ByMaximumSize(groupSize:Int) extends GroupingStrategy {
  override def addNewPerson(g:GroupSet,person:String):GroupSet = {
    val oldGroups = g.groups
    val newGroups = {
      oldGroups.find(group => {
        group.members.length < groupSize
      }).map(fg => {
        fg.copy(members = person :: fg.members) :: oldGroups.filter(_.id != fg.id)
      }).getOrElse({
        Group(nextFuncName,g.location,new Date().getTime,List(person)) :: oldGroups
      })
    }
    warn("ByMaximumSize adding %s yields %s".format(person,newGroups))
    g.copy(groups = newGroups)
  }
}
case class ByTotalGroups(numberOfGroups:Int) extends GroupingStrategy {
  override def addNewPerson(g:GroupSet,person:String):GroupSet = {
    if(g.contains(person)){
      trace("Already grouped: %s".format(person))
      g
    }
    else{
      val oldGroups = g.groups
      val newGroups = g.copy(groups = {
        oldGroups match {
          case l:List[Group] if l.length < numberOfGroups => {
            trace("Adding a group")
            Group(nextFuncName,g.location,new Date().getTime,List(person)) :: l
          }
          case l:List[Group] => {
            trace("Adding to an existing group")
            l.sortWith((a,b) => a.members.length < b.members.length).headOption.map(fg => {
              trace("  Adding to %s".format(fg))
              fg.copy(members = person :: fg.members) :: l.filter(_.id != fg.id)
            }).getOrElse({
              trace("  Adding to %s".format(l.head))
              l.head.copy(members = person :: l.head.members) :: l.drop(1)
            })
          }
        }
      })
      trace("ByTotalGroups adding %s yields %s".format(person,newGroups))
      newGroups
    }
  }
}
case class ComplexGroupingStrategy(data:Map[String,String]) extends GroupingStrategy {
  protected var groupingFunction:Tuple2[GroupSet,String]=>GroupSet = (t:Tuple2[GroupSet,String]) => t._1
  override def addNewPerson(g:GroupSet,person:String):GroupSet = {
    groupingFunction((g,person))
  }
  def replaceGroupingFunction(func:Tuple2[GroupSet,String]=>GroupSet):ComplexGroupingStrategy = {
    groupingFunction = func
    this
  }
}
case object OnePersonPerGroup extends GroupingStrategy {
  override def addNewPerson(g:GroupSet,person:String):GroupSet = {
    g.copy(groups = Group(nextFuncName,g.location,new Date().getTime,List(person)) :: g.groups)
  }
}
case object EveryoneInOneGroup extends GroupingStrategy {
  override def addNewPerson(g:GroupSet,person:String):GroupSet = {
    g.copy(groups = List(Group(g.groups.headOption.map(_.id).getOrElse(nextFuncName),g.location,new Date().getTime,(person :: g.groups.flatMap(_.members)).distinct)))
  }
}

case class Group(id:String,location:String,timestamp:Long,members:List[String],override val audiences:List[Audience] = Nil) extends MeTLData(audiences)
object Group {
  def empty = Group("","",0,Nil,Nil)
}

case class Conversation(author:String,lastAccessed:Long,slides:List[Slide],subject:String,tag:String,jid:String,title:String,created:Long,permissions:Permissions, blackList:List[String] = List.empty[String],override val audiences:List[Audience] = Nil,foreignRelationship:Option[ForeignRelationship] = None) extends MeTLData(audiences) with Logger{
  def delete = copy(subject="deleted",lastAccessed=new Date().getTime)//Conversation(author,new Date().getTime,slides,"deleted",tag,jid,title,created,permissions,blackList,audiences)
  def rename(newTitle:String) = copy(title=newTitle,lastAccessed = new Date().getTime)
  def replacePermissions(newPermissions:Permissions) = copy(permissions = newPermissions, lastAccessed = new Date().getTime)
  def shouldDisplayFor(username:String,userGroups:List[String]):Boolean = {
    val trimmedSubj = subject.toLowerCase.trim
    (trimmedSubj == "unrestricted" || author.toLowerCase.trim == username.toLowerCase.trim || userGroups.exists(ug => ug.toLowerCase.trim == trimmedSubj)) && trimmedSubj != "deleted"
  }
  def replaceSubject(newSubject:String) = copy(subject=newSubject,lastAccessed=new Date().getTime)
  def addSlideAtIndex(index:Int,slide:Slide) = {
    val oldSlides = slides.map(s => {
      if (s.index >= index){
        s.replaceIndex(s.index + 1)
      } else {
        s
      }
    })
    val newSlides = slide.copy(index = index) :: oldSlides
    replaceSlides(newSlides)
  }
  def replaceSlides(newSlides:List[Slide]) = copy(slides=newSlides,lastAccessed = new Date().getTime)
  def setForeignRelationship(fr:Option[ForeignRelationship]) = copy(foreignRelationship = fr,lastAccessed=new Date().getTime)
}
object Conversation{
  def empty = Conversation("",0L,List.empty[Slide],"","","","",0L,Permissions.default,Nil,Nil)
}

case class Slide(author:String,id:String,index:Int,defaultHeight:Int = 540, defaultWidth:Int = 720, exposed:Boolean = true, slideType:String = "SLIDE",groupSet:List[GroupSet] = Nil,override val audiences:List[Audience] = Nil) extends MeTLData(audiences){
  def replaceIndex(newIndex:Int) = copy(index=newIndex)
}
object Slide{
  def empty = Slide("","",0)
}

case class Audience(domain:String,name:String,audienceType:String,action:String,override val audiences:List[Audience] = Nil) extends MeTLData(audiences)
object Audience {
  def empty = Audience("","","","")
  def default = Audience("","","","")
}

case class Permissions(studentsCanOpenFriends:Boolean,studentsCanPublish:Boolean,usersAreCompulsorilySynced:Boolean,studentsMayBroadcast:Boolean,studentsMayChatPublicly:Boolean) extends MeTLData(Nil)
object Permissions{
  def empty = Permissions(false,false,false,false,false)
  def default = Permissions(false,true,false,true,true)
}

abstract class MeTLData(val audiences:List[Audience] = Nil){
}
object MeTLData {
  def unapply(in:MeTLData) = Some((in.audiences))
}

case class MeTLUnhandledData(unhandled:String,valueType:String,override val audiences:List[Audience] = Nil) extends MeTLData(audiences)
object MeTLUnhandledData {
  def empty = MeTLUnhandledData("","null")
  def empty(unhandled:String,valueType:String) = MeTLUnhandledData(unhandled,valueType)
}
case class MeTLUnhandledStanza(override val author:String,override val timestamp:Long,unhandled:String,valueType:String,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime) = Stopwatch.time("MeTLUnhandledStanza.adjustTimestamp",copy(timestamp = newTime))
}
abstract class MeTLStanza(val author:String,val timestamp:Long,override val audiences:List[Audience] = Nil) extends MeTLData(audiences){
  def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLStanza
}
object MeTLStanza{
  def unapply(in:MeTLStanza) = Some((in.author,in.timestamp,in.audiences))
}

case class Presentation(conversation:Conversation,stanzas:Map[Int,List[MeTLStanza]] = Map.empty[Int,List[MeTLStanza]],metaData:List[Tuple2[String,String]] = List.empty[Tuple2[String,String]],override val audiences:List[Audience] = Nil) extends MeTLData(audiences)
object MeTLUnhandledStanza {
  def empty = MeTLUnhandledStanza("",0L,"","null")
  def empty(unhandled:String,valueType:String) = MeTLUnhandledStanza("",0L,unhandled,valueType)
}
case class Theme(author:String,text:String,origin:String)

object MeTLTheme {
  def empty = MeTLTheme("",0L,"",Theme("","",""),Nil)
}
case class MeTLTheme(override val author:String,override val timestamp:Long,location:String,theme:Theme,override val audiences:List[Audience]) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTimestamp:Long) = copy(timestamp = newTimestamp)
}
object Profile {
  def empty = Profile(0L,"","",Map.empty[String,String],Nil)
}
case class Profile(override val timestamp:Long, id:String, name:String, attributes:Map[String,String],override val audiences:List[Audience] = Nil) extends MeTLStanza("",timestamp,audiences){
  override def adjustTimestamp(newTimestamp:Long) = copy(timestamp = newTimestamp)
}

case class Attendance(override val author:String,override val timestamp:Long,location:String,present:Boolean,override val audiences:List[Audience]) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime) = Stopwatch.time("Attendance.adjustTimestamp",copy(timestamp = newTime))
}
object Attendance{
  def empty = Attendance("",0L,"",false,Nil)
}

case class MeTLUnhandledCanvasContent(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil, override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0,unhandled:String,valueType:String) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY){
  override def adjustTimestamp(newTimestamp: Long): com.metl.data.MeTLCanvasContent = copy(timestamp = newTimestamp)
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def alterPrivacy(newPrivacy: com.metl.data.Privacy.Privacy): com.metl.data.MeTLCanvasContent = copy(privacy = newPrivacy)
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def generateNewIdentity(descriptor: String): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLUnhandledCanvasContent {
  def empty = MeTLUnhandledCanvasContent("",0L,"",Privacy.NOT_SET,"","",Nil,1.0,1.0,"","null")
  def empty(unhandled:String,valueType:String) = MeTLUnhandledCanvasContent("",0L,"",Privacy.NOT_SET,"","",Nil,1.0,1.0,unhandled,valueType)
}
abstract class MeTLCanvasContent(override val author:String,override val timestamp:Long,val target:String,val privacy:Privacy,val slide:String,val identity:String,override val audiences:List[Audience] = Nil,val scaleFactorX:Double = 1.0,val scaleFactorY:Double = 1.0) extends MeTLStanza(author,timestamp,audiences) {
  protected def genNewIdentity(role:String) = "%s:%s:%s_from:%s".format(new java.util.Date().getTime.toString,author,role,identity).take(256)
  def left:Double = 0.0
  def right:Double = 0.0
  def top:Double = 0.0
  def bottom:Double = 0.0
  def scale(factor:Double):MeTLCanvasContent
  def scale(xScale:Double,yScale:Double):MeTLCanvasContent
  def alterPrivacy(newPrivacy:Privacy):MeTLCanvasContent
  def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double):MeTLCanvasContent
  override def adjustTimestamp(newTimestamp:Long):MeTLCanvasContent
  def generateDirty(dirtyTime:Long):MeTLCanvasContent
  def matches(other:MeTLCanvasContent):Boolean = other.identity == identity && other.privacy == privacy && other.slide == slide
  def isDirtiedBy(other:MeTLCanvasContent):Boolean = false
  def isDirtierFor(other:MeTLCanvasContent):Boolean = false
  def generateNewIdentity(descriptor:String):MeTLCanvasContent
}
object MeTLCanvasContent{
  def unapply(in:MeTLCanvasContent) = Some((in.author,in.timestamp,in.target,in.privacy,in.slide,in.identity,in.audiences,in.scaleFactorX,in.scaleFactorY))
}
object MeTLTextWord {
  def empty = MeTLTextWord("",false,false,false,"",Color.empty,"",0.0)
}

case class MeTLChatMessage(override val author:String, override val timestamp:Long, identity:String, contentType:String, content:String, context:String, override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLChatMessage = Stopwatch.time("MeTLChatMessage.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  def adjustAudience(newAudiences:List[Audience]):MeTLChatMessage = copy(audiences = newAudiences)
}
object MeTLChatMessage {
  def empty = MeTLChatMessage("",0L,"","","","",Nil)
}

case class MeTLTextWord(text:String,bold:Boolean,underline:Boolean,italic:Boolean,justify:String,color:Color,font:String,size:Double){
  def scale(factor:Double):MeTLTextWord = copy(size=size * factor)
}
case class MeTLMultiWordText(override val author:String,override val timestamp:Long,height:Double,width:Double,requestedWidth:Double,x:Double,y:Double,tag:String,override val identity:String,override val target:String,override val privacy:Privacy,override val slide:String,words:Seq[MeTLTextWord],override val audiences:List[Audience] = Nil,override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY) {
  override def isDirtiedBy(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyText => matches(o) && o.timestamp > timestamp
    case _ => false
  }
  override def left:Double = x
  override def right:Double = x+width
  override def top:Double = y
  override def bottom:Double = y+height
  override def scale(factor:Double):MeTLMultiWordText = scale(factor,factor)
  override def scale(xScale:Double,yScale:Double):MeTLMultiWordText = Stopwatch.time("MeTLMultiWordText.scale",{
    val averageFactor = (xScale + yScale) / 2
    copy(
      height = height * yScale,
      width = width * xScale,
      x = x * xScale,
      y = y * yScale,
      scaleFactorX = scaleFactorX * xScale,
      scaleFactorY = scaleFactorY * yScale,
      words = words.map(_.scale(averageFactor))
    )
  })
  override def alterPrivacy(possiblyNewPrivacy:Privacy):MeTLMultiWordText = Stopwatch.time("MeTLMultiWordText.alterPrivacy",{
    possiblyNewPrivacy match {
      case p:Privacy if (p == privacy) => this
      case Privacy.NOT_SET => this
      case p:Privacy => copy(privacy = p)
      case _ => this
    }
  })
  override def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double):MeTLMultiWordText = Stopwatch.time("MeTLMultiWordText.adjustVisual",{
    val averageFactor = (xScale + yScale) / 2
    copy(words = words.map(_.scale(xScale)), height = height * yScale, width = width * xScale, x = x + xTranslate, y = y + yTranslate)
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLMultiWordText = Stopwatch.time("MeTLMultiWordText.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateDirty(dirtyTime:Long = new java.util.Date().getTime):MeTLDirtyText = Stopwatch.time("MeTLMultiWordText.generateDirty",{
    MeTLDirtyText(author,dirtyTime,target,privacy,slide,identity)
  })
  override def generateNewIdentity(descriptor:String):MeTLMultiWordText = copy(identity = genNewIdentity("newText:"+descriptor))
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLMultiWordText => super.matches(o)
    case _ => false
  }
}

object MeTLMultiWordText{
  def empty = MeTLMultiWordText("",0L,0.0,0.0,0.0,0.0,0.0,"","","",Privacy.NOT_SET,"",List.empty[MeTLTextWord])
}

case class MeTLInk(override val author:String,override val timestamp:Long,checksum:Double,startingSum:Double,points:List[Point],color:Color,thickness:Double,isHighlighter:Boolean,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil,override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY) {
  private def offsetAt(point:Point) = point.thickness/256*thickness
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLInk => super.matches(o)
    case _ => false
  }
  override def isDirtiedBy(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyInk => matches(o) && o.timestamp > timestamp
    case _ => false
  }
  override val left:Double = points.map(p => p.x-offsetAt(p)).min
  override val right:Double = points.map(p => p.x+offsetAt(p)).max
  override val top:Double = points.map(p => p.y-offsetAt(p)).min
  override val bottom:Double = points.map(p => p.y+offsetAt(p)).max
  override def scale(factor:Double):MeTLInk = scale(factor,factor)
  override def scale(xScale:Double,yScale:Double):MeTLInk = Stopwatch.time("MeTLInk.scale",{
    val averageFactor = (xScale + yScale) / 2
    copy(points = points.map(p => Point(p.x*xScale,p.y*yScale,p.thickness)),thickness = thickness*averageFactor,scaleFactorX = scaleFactorX * xScale,scaleFactorY = scaleFactorY * yScale)
  })
  override def alterPrivacy(newPrivacy:Privacy):MeTLInk = Stopwatch.time("MeTLInk.alterPrivacy",{
    newPrivacy match {
      case p:Privacy if (p == privacy) => this
      case Privacy.NOT_SET => this
      case p:Privacy => copy(privacy = p)
      case _ => this
    }
  })
  override def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double) = Stopwatch.time("MeTLInk.adjustVisual",{
    val averageFactor = (xScale + yScale) / 2
    val newPoints = (xTranslate,yTranslate,xScale,yScale) match {
      case (0,0,1.0,1.0) => points
      case (xO,yO,1.0,1.0) => points.map(p => Point(p.x+xO,p.y+yO,p.thickness))
      case (0,0,xS,yS) => points.map(p => Point((((p.x - left) * xS) + left),(((p.y - top) * yS) + top),p.thickness))
      case (xO,yO,xS,yS) => points.map(p => Point((((p.x - left) * xS) + left + xO),(((p.y - top) * yS) + top + yO),p.thickness))
    }
    copy(points = newPoints, thickness = thickness * averageFactor)
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLInk = Stopwatch.time("MeTLInk.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateDirty(dirtyTime:Long = new java.util.Date().getTime):MeTLDirtyInk = Stopwatch.time("MeTLInk.generateDirty",{
    MeTLDirtyInk(author,dirtyTime,target,privacy,slide,identity,audiences)
  })
  override def generateNewIdentity(descriptor:String):MeTLInk = copy(identity = genNewIdentity("newInk:"+descriptor))
}

object MeTLInk{
  def empty = MeTLInk("",0L,0.0,0.0,List.empty[Point],Color.default,0.0,false,"",Privacy.NOT_SET,"","")
}

case class MeTLImage(override val author:String,override val timestamp:Long,tag:String,source:Box[String],imageBytes:Box[Array[Byte]],pngBytes:Box[Array[Byte]],width:Double,height:Double,x:Double,y:Double,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil,override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLImage => super.matches(o)
    case _ => false
  }
  override def isDirtiedBy(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyImage => matches(o) && o.timestamp > timestamp
    case _ => false
  }
  override val left:Double = x
  override val right:Double = x+width
  override val top:Double = y
  override val bottom:Double = y+height
  override def scale(factor:Double):MeTLImage = scale(factor,factor)
  override def scale(xScale:Double,yScale:Double):MeTLImage = Stopwatch.time("MeTLImage.scale",{
    copy(width = width * xScale, height = height * yScale, x = x * xScale, y = y * yScale, scaleFactorX = scaleFactorX * xScale , scaleFactorY = scaleFactorY * yScale)
  })
  override def alterPrivacy(possiblyNewPrivacy:Privacy):MeTLImage = Stopwatch.time("MeTLImage.alterPrivacy",{
    possiblyNewPrivacy match {
      case p:Privacy if (p == privacy) => this
      case Privacy.NOT_SET => this
      case p:Privacy => copy(privacy = p)
      case _ => this
    }
  })
  override def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double):MeTLImage = Stopwatch.time("MeTLImage.adjustVisual",{
    copy(width = width * xScale, height = height * yScale, x = x + xTranslate, y = y+ yTranslate)
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLImage = Stopwatch.time("MeTLimage.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateDirty(dirtyTime:Long = new java.util.Date().getTime):MeTLDirtyImage = Stopwatch.time("MeTLImage.generateDirty",{
    MeTLDirtyImage(author,dirtyTime,target,privacy,slide,identity)
  })
  override def generateNewIdentity(descriptor:String):MeTLImage = copy(identity = genNewIdentity("newImage:"+descriptor))
  override def equals(other:Any):Boolean = other match {
    case MeTLImage(a,ts,t,s,_ib,_pb,iw,ih,ix,iy,ta,p,sl,id,aus,sfx,sfy) => a == author && ts == timestamp && t == tag && s == source && iw == width && ih == height && ix == x && iy == y && ta == target && p == privacy && sl == slide && id == identity && aus == audiences && sfx == scaleFactorX && sfy == scaleFactorY
    case _ => false
  }
}

object MeTLImage{
  def empty = MeTLImage("",0L,"",Empty,Empty,Empty,0.0,0.0,0.0,0.0,"",Privacy.NOT_SET,"","")
}

case class MeTLVideo(override val author:String,override val timestamp:Long,source:Box[String],videoBytes:Box[Array[Byte]],width:Double,height:Double,x:Double,y:Double,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil,override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLVideo => super.matches(o)
    case _ => false
  }
  override def isDirtiedBy(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyVideo => matches(o) && o.timestamp > timestamp
    case _ => false
  }
  override val left:Double = x
  override val right:Double = x+width
  override val top:Double = y
  override val bottom:Double = y+height
  override def scale(factor:Double):MeTLVideo = scale(factor,factor)
  override def scale(xScale:Double,yScale:Double):MeTLVideo = Stopwatch.time("MeTLVideo.scale",{
    copy(width = width * xScale, height = height * yScale, x = x * xScale, y = y * yScale, scaleFactorX = scaleFactorX * xScale , scaleFactorY = scaleFactorY * yScale)
  })
  override def alterPrivacy(possiblyNewPrivacy:Privacy):MeTLVideo = Stopwatch.time("MeTLVideo.alterPrivacy",{
    possiblyNewPrivacy match {
      case p:Privacy if (p == privacy) => this
      case Privacy.NOT_SET => this
      case p:Privacy => copy(privacy = p)
      case _ => this
    }
  })
  override def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double):MeTLVideo = Stopwatch.time("MeTLVideo.adjustVisual",{
    copy(width = width * xScale, height = height * yScale, x = x + xTranslate, y = y+ yTranslate)
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLVideo = Stopwatch.time("MeTLVideo.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateDirty(dirtyTime:Long = new java.util.Date().getTime):MeTLDirtyVideo = Stopwatch.time("MeTLVideo.generateDirty",{
    MeTLDirtyVideo(author,dirtyTime,target,privacy,slide,identity)
  })
  override def generateNewIdentity(descriptor:String):MeTLVideo = copy(identity = genNewIdentity("newVideo:"+descriptor))
}

object MeTLVideo{
  def empty = MeTLVideo("",0L,Empty,Empty,0.0,0.0,0.0,0.0,"",Privacy.NOT_SET,"","")
}



case class MeTLText(override val author:String,override val timestamp:Long,text:String,height:Double,width:Double,caret:Int,x:Double,y:Double,tag:String,style:String,family:String,weight:String,size:Double,decoration:String,override val identity:String,override val target:String,override val privacy:Privacy,override val slide:String,color:Color,override val audiences:List[Audience] = Nil,override val scaleFactorX:Double = 1.0,override val scaleFactorY:Double = 1.0) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences,scaleFactorX,scaleFactorY) {
  lazy val isRichText = {
    try {
      scala.xml.XML.loadString(text).namespace.trim.toLowerCase == "http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    } catch {
      case e:Exception => false
    }
  }
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLText => super.matches(o)
    case _ => false
  }
  override def isDirtiedBy(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyInk => matches(o) && o.timestamp > timestamp
    case _ => false
  }
  override def left:Double = x
  override def right:Double = x+width
  override def top:Double = y
  override def bottom:Double = y+height
  import scala.xml._
  protected def replaceAttr(attributes:scala.xml.MetaData,attrName:String,attrTransform:String=>String):scala.xml.MetaData = {
    attributes match {
      case p:PrefixedAttribute => new PrefixedAttribute(pre=p.pre,key=p.key,value= { p.key match {
        case an:String if an == attrName => attrTransform(p.value.text)
        case other => p.value.text
      }}, next=replaceAttr(p.next,attrName,attrTransform))
      case u:UnprefixedAttribute => new UnprefixedAttribute(key=u.key,value= { u.key match {
        case an:String if an == attrName => attrTransform(u.value.text)
        case other => u.value.text
      }}, next=replaceAttr(u.next,attrName,attrTransform))
      case _ => Null
    }
  }
  protected def scaleRichText(t:String,factor:Double):String = {
    try {
      scala.xml.XML.loadString(t) match {
        case section:Elem if section.label == "Section" => {
          section.copy(attributes = replaceAttr(section.attributes,"FontSize",(a) => (a.toDouble * factor).toString),child = {
            (section \ "Paragraph").map{
              case page:Elem if page.label == "Paragraph" => {
                page.copy(attributes = replaceAttr(page.attributes,"FontSize",(a) => (a.toDouble * factor).toString),child = {
                  (page \ "Run").map{
                    case run:Elem if run.label == "Run" => {
                      run.copy(attributes = replaceAttr(run.attributes,"FontSize",(a) => (a.toDouble * factor).toString))
                    }
                    case other => other
                  }
                })
              }
              case other => other
            }
          }).toString
        }
        case _other => t
      }
    } catch {
      case e:Exception => t
    }
  }
  override def scale(factor:Double):MeTLText = scale(factor,factor)
  override def scale(xScale:Double,yScale:Double):MeTLText = Stopwatch.time("MeTLText.scale",{
    val averageFactor = (xScale + yScale) / 2
    if (isRichText){
      copy(text = scaleRichText(text,averageFactor), height = height * yScale, width = width * xScale, x = x * xScale, y = y * yScale, size = size * averageFactor, scaleFactorX = scaleFactorX * xScale, scaleFactorY = scaleFactorY * yScale)
    } else {
      copy(height = height * yScale, width = width * xScale, x = x * xScale, y = y * yScale, size = size * averageFactor, scaleFactorX = scaleFactorX * xScale, scaleFactorY = scaleFactorY * yScale)
    }
  })
  override def alterPrivacy(possiblyNewPrivacy:Privacy):MeTLText = Stopwatch.time("MeTLText.alterPrivacy",{
    possiblyNewPrivacy match {
      case p:Privacy if (p == privacy) => this
      case Privacy.NOT_SET => this
      case p:Privacy => copy(privacy = p)
      case _ => this
    }
  })
  override def adjustVisual(xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double):MeTLText = Stopwatch.time("MeTLText.adjustVisual",{
    val averageFactor = (xScale + yScale) / 2
    if (isRichText){
      copy(text = scaleRichText(text,averageFactor), height = height * yScale, width = width * xScale, x = x + xTranslate, y = y + yTranslate, size = size * averageFactor)
    } else {
      copy(height = height * yScale, width = width * xScale, x = x + xTranslate, y = y + yTranslate, size = size * averageFactor)
    }
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLText = Stopwatch.time("MeTLText.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateDirty(dirtyTime:Long = new java.util.Date().getTime):MeTLDirtyText = Stopwatch.time("MeTLText.generateDirty",{
    MeTLDirtyText(author,dirtyTime,target,privacy,slide,identity)
  })
  override def generateNewIdentity(descriptor:String):MeTLText = copy(identity = genNewIdentity("newText:"+descriptor))
}

object MeTLText{
  def empty = MeTLText("",0L,"",0.0,0.0,0,0.0,0.0,"","","","",0.0,"","","",Privacy.NOT_SET,"",Color.default)
}

case class MeTLMoveDelta(override val author:String,override val timestamp:Long,override val target:String, override val privacy:Privacy,override val slide:String,override val identity:String,xOrigin:Double,yOrigin:Double,
  inkIds:Seq[String],
  textIds:Seq[String],
  multiWordTextIds:Seq[String],
  imageIds:Seq[String],
  videoIds:Seq[String],
  xTranslate:Double,yTranslate:Double,xScale:Double,yScale:Double,newPrivacy:Privacy,isDeleted:Boolean,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences){
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLMoveDelta => super.matches(o)
    case _ => false
  }
  override def generateNewIdentity(descriptor:String):MeTLMoveDelta = copy(identity = genNewIdentity("newMeTLMoveDelta:"+descriptor))
  def generateDirtier(newInkIds:Seq[String],newTextIds:Seq[String],newMultiWordTextIds:Seq[String],newImageIds:Seq[String],newVideoIds:Seq[String],replacementPrivacy:Privacy):MeTLMoveDelta = Stopwatch.time("MeTLMoveDelta.generateDirtier",{
    copy(privacy = replacementPrivacy,identity = genNewIdentity("dirtierGeneratedFrom(%s)".format(identity)),
      inkIds = newInkIds,
      textIds = newTextIds,
      multiWordTextIds = newMultiWordTextIds,
      imageIds = newImageIds,
      videoIds = newVideoIds,
      xTranslate = 0.0,yTranslate = 0.0,xScale = 1.0,yScale = 1.0,isDeleted = true)
  })
  def replaceIds(newInkIds:Seq[String],newTextIds:Seq[String],newMultiWordTextIds:Seq[String],newImageIds:Seq[String],newVideoIds:Seq[String],replacementPrivacy:Privacy):MeTLMoveDelta = Stopwatch.time("MeTLMoveDelta.replaceIds",{
    copy(privacy = replacementPrivacy,identity = genNewIdentity("adjusterGeneratedFrom(%s)".format(identity)),inkIds = newInkIds,textIds = newTextIds,multiWordTextIds = newMultiWordTextIds, imageIds = newImageIds, videoIds = newVideoIds)
  })
  override def adjustVisual(newXTranslate:Double,newYTranslate:Double,newXScale:Double,newYScale:Double):MeTLMoveDelta = Stopwatch.time("MeTLMoveDelta.adjustVisual",{
    copy(xOrigin = xOrigin + newXTranslate,yOrigin = yOrigin + newYTranslate,xTranslate = xTranslate + newXTranslate,yTranslate = yTranslate + newYTranslate,xScale = xScale * newXScale,yScale = yScale * newYScale,privacy = newPrivacy)
  })
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLMoveDelta = Stopwatch.time("MeTLMoveDelta.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def scale(newXScale:Double,newYScale:Double):MeTLMoveDelta = {
    copy(xTranslate = xTranslate * newXScale,yTranslate= yTranslate * newYScale,xScale = xScale * newXScale,yScale = yScale * newYScale,privacy = newPrivacy)
  }
  def adjustIndividualContent(cc:MeTLCanvasContent,shouldTestPrivacy:Boolean = true,possiblyOverrideLeftBounds:Double = 0.0,possiblyOverrideTopBounds:Double = 0.0):MeTLCanvasContent = {
    val thisMdLeft = xOrigin match {
      case Double.NaN => possiblyOverrideLeftBounds
      case d:Double => d
    }
    val thisMdTop = yOrigin match {
      case Double.NaN => possiblyOverrideTopBounds
      case d:Double => d
    }
    val internalX = cc.left - thisMdLeft
    val internalY = cc.top - thisMdTop
    val offsetX = -(internalX - (internalX * xScale));
    val offsetY = -(internalY - (internalY * yScale));
    cc match {
      case i:MeTLInk if (isDirtierFor(i,shouldTestPrivacy)) => i.adjustVisual(xTranslate + offsetX,yTranslate + offsetY,xScale,yScale).adjustTimestamp(timestamp).alterPrivacy(newPrivacy)
      case t:MeTLText if (isDirtierFor(t,shouldTestPrivacy)) => t.adjustVisual(xTranslate + offsetX,yTranslate + offsetY,xScale,yScale).adjustTimestamp(timestamp).alterPrivacy(newPrivacy)
      case t:MeTLMultiWordText if (isDirtierFor(t,shouldTestPrivacy)) => t.adjustVisual(xTranslate + offsetX,yTranslate + offsetY,xScale,yScale).adjustTimestamp(timestamp).alterPrivacy(newPrivacy)
      case i:MeTLImage if (isDirtierFor(i,shouldTestPrivacy)) => i.adjustVisual(xTranslate + offsetX,yTranslate + offsetY,xScale,yScale).adjustTimestamp(timestamp).alterPrivacy(newPrivacy)
      case i:MeTLVideo if (isDirtierFor(i,shouldTestPrivacy)) => i.adjustVisual(xTranslate + offsetX,yTranslate + offsetY,xScale,yScale).adjustTimestamp(timestamp).alterPrivacy(newPrivacy)
      case _ => cc
    }
  }
  def generateChanges(rawPublicHistory:History,rawPrivateHistory:History):Tuple2[List[MeTLStanza],Map[String,List[MeTLStanza]]] = Stopwatch.time("MeTLMoveDelta.generateChanges",{
    val privateHistory = rawPrivateHistory.filterCanvasContentsForMoveDelta(this)
    val publicHistory = rawPublicHistory.filterCanvasContentsForMoveDelta(this)
    val (publicTexts,publicHighlighters,publicInks,publicImages,publicMultiWordTexts,publicVideos) = publicHistory.getRenderableGrouped
    val (privateTexts,privateHighlighters,privateInks,privateImages,privateMultiWordTexts,privateVideos) = privateHistory.getRenderableGrouped
    newPrivacy match {
      case p:Privacy if p == Privacy.PUBLIC => {
        val notP = Privacy.PRIVATE
        val privateInksToPublicize = privateInks.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateHighlightersToPublicize = privateHighlighters.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateTextsToPublicize = privateTexts.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateImagesToPublicize = privateImages.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateMultiWordTextsToPublicize = privateMultiWordTexts.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateVideosToPublicize = privateVideos.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val privateAuthors = (privateInks ::: privateHighlighters ::: privateTexts ::: privateImages ::: privateMultiWordTexts ::: privateVideos).map(_.author).distinct
        val privateDirtiers = (privateAuthors.length > 0) match {
          case true => Map(privateAuthors.map(pa => (
            pa,
            List(generateDirtier(
              privateInks.filter(_.author == pa).map(i => i.identity) ::: privateHighlighters.filter(_.author == pa).map(i => i.identity),
              privateTexts.filter(_.author == pa).map(i => i.identity),
              privateMultiWordTexts.filter(_.author == pa).map(i => i.identity),
              privateImages.filter(_.author == pa).map(i => i.identity),
              privateVideos.filter(_.author == pa).map(i => i.identity),notP)
            ))
          ):_*)
          case _ => Map.empty[String,List[MeTLStanza]]
        }
        val publicAdjuster = ((publicInks ::: publicHighlighters ::: publicTexts ::: publicImages ::: publicMultiWordTexts ::: publicVideos).length > 0) match {
          case true => List(replaceIds(
            publicInks.map(i=>i.identity) ::: publicHighlighters.map(i => i.identity),
            publicTexts.map(i=>i.identity),
            publicMultiWordTexts.map(i=>i.identity),
            publicImages.map(i=>i.identity),
            publicVideos.map(i=>i.identity),p))
          case _ => List.empty[MeTLStanza]
        }
        (publicAdjuster :::
          privateInksToPublicize :::
          privateHighlightersToPublicize :::
          privateTextsToPublicize :::
          privateImagesToPublicize :::
          privateVideosToPublicize :::
          privateMultiWordTextsToPublicize, privateDirtiers)
      }
      case p:Privacy if p == Privacy.PRIVATE => {
        val notP = Privacy.PUBLIC
        val publicInksToPrivatize = publicInks.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicHighlightersToPrivatize = publicHighlighters.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicTextsToPrivatize = publicTexts.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicImagesToPrivatize = publicImages.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicVideosToPrivatize = publicVideos.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicMultiWordTextsToPrivatize = publicMultiWordTexts.map(i => adjustIndividualContent(i,false).generateNewIdentity("adjustedBy(%s)".format(identity)))
        val publicDirtiers = ((publicInks ::: publicHighlighters ::: publicTexts ::: publicImages ::: publicMultiWordTexts ::: publicVideos).length > 0) match {
          case true => List(generateDirtier(
            publicInks.map(i => i.identity) ::: publicHighlighters.map(i => i.identity),
            publicTexts.map(i => i.identity),
            publicMultiWordTexts.map(i => i.identity),
            publicImages.map(i => i.identity),
            publicVideos.map(i => i.identity),notP))
          case _ => List.empty[MeTLStanza]
        }
        val publicContent = (publicInks ::: publicHighlighters ::: publicTexts ::: publicImages ::: publicMultiWordTexts ::: publicVideos)
        val privateContent = (privateInks ::: privateHighlighters ::: privateTexts ::: privateImages ::: privateMultiWordTexts ::: privateVideos)
        val publicAuthors = publicContent.map(_.author).distinct
        val privateAuthors = privateContent.map(_.author).distinct
        val privateAdjusters = (publicAuthors.length > 0 || privateAuthors.length > 0) match {
          case true => Map(publicAuthors.map(pa => (
            pa,
            publicInksToPrivatize.filter(_.author == pa) :::
              publicHighlightersToPrivatize.filter(_.author == pa) :::
              publicTextsToPrivatize.filter(_.author == pa) :::
              publicImagesToPrivatize.filter(_.author == pa) :::
              publicMultiWordTextsToPrivatize.filter(_.author == pa) :::
              publicVideosToPrivatize.filter(_.author == pa) :::
              List(replaceIds(
                privateInks.filter(_.author == pa).map(i => i.identity) ::: privateHighlighters.map(i => i.identity),
                privateTexts.filter(_.author == pa).map(i => i.identity),
                privateMultiWordTexts.filter(_.author == pa).map(i => i.identity),
                privateImages.filter(_.author == pa).map(i => i.identity),
                privateVideos.filter(_.author == pa).map(i => i.identity),p)
              ))
          ):_*)
          case _ => Map.empty[String,List[MeTLStanza]]
        }
        (publicDirtiers,privateAdjusters)
      }
      case _ => {
        val privAuthors = (privateInks ::: privateHighlighters ::: privateTexts ::: privateImages ::: privateMultiWordTexts ::: privateVideos).map(_.author).distinct
        val privDeltas = (privAuthors.length > 0) match {
          case true => Map(privAuthors.map(pa => (
            pa,
            List(replaceIds(
              privateInks.filter(_.author == pa).map(i=>i.identity) ::: privateHighlighters.map(i => i.identity),
              privateTexts.filter(_.author == pa).map(i=>i.identity),
              privateMultiWordTexts.filter(_.author == pa).map(i=>i.identity),
              privateImages.filter(_.author == pa).map(i=>i.identity),
              privateVideos.filter(_.author == pa).map(i=>i.identity),Privacy.PRIVATE)
            )
          )):_*)
          case _ => Map.empty[String,List[MeTLStanza]]
        }
        val pubDelta = ((publicInks ::: publicHighlighters ::: publicTexts ::: publicImages ::: publicMultiWordTexts ::: publicVideos).length > 0) match {
          case true => List(replaceIds(
            publicInks.map(i=>i.identity) ::: publicHighlighters.map(i => i.identity),
            publicTexts.map(i=>i.identity),
            publicMultiWordTexts.map(i=>i.identity),
            publicImages.map(i=>i.identity),
            publicVideos.map(i=>i.identity),Privacy.PUBLIC))
          case _ => List.empty[MeTLStanza]
        }
        (pubDelta,privDeltas)
      }
    }
  })
  override def isDirtierFor(other:MeTLCanvasContent):Boolean = isDirtierFor(other,true)
  def isDirtierFor(other:MeTLCanvasContent, testPrivacy:Boolean = true):Boolean = other match {
    case i:MeTLInk => ((!testPrivacy) || privacy == i.privacy) && timestamp > i.timestamp && i.slide == slide && inkIds.contains(i.identity)
    case i:MeTLImage => ((!testPrivacy) || privacy == i.privacy) && timestamp > i.timestamp && i.slide == slide && imageIds.contains(i.identity)
    case i:MeTLVideo => ((!testPrivacy) || privacy == i.privacy) && timestamp > i.timestamp && i.slide == slide && videoIds.contains(i.identity)
    case i:MeTLText => ((!testPrivacy) || privacy == i.privacy) && timestamp > i.timestamp && i.slide == slide && textIds.contains(i.identity)
    case i:MeTLMultiWordText => ((!testPrivacy) || privacy == i.privacy) && timestamp > i.timestamp && i.slide == slide && multiWordTextIds.contains(i.identity)
    case _ => false
  }
  def alterPrivacy(newPrivacy: com.metl.data.Privacy.Privacy): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
case object MeTLMoveDelta{
  def empty = MeTLMoveDelta("",0L,"",Privacy.NOT_SET,"","",0.0,0.0,Nil,Nil,Nil,Nil,Nil,0.0,0.0,1.0,1.0,Privacy.NOT_SET,false,Nil)
}

case class MeTLDirtyInk(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyInk => super.matches(o)
    case _ => false
  }
  override def isDirtierFor(other:MeTLCanvasContent) = other match {
    case o:MeTLInk => super.matches(o) && o.timestamp < timestamp
    case _ => false
  }
  override def alterPrivacy(newPrivacy:Privacy):MeTLDirtyInk = copy(privacy = newPrivacy)
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLDirtyInk = Stopwatch.time("MeTLDirtyInk.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def generateNewIdentity(descriptor:String):MeTLDirtyInk = copy(identity = genNewIdentity("newMeTLDirtyInk:"+descriptor))
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLDirtyInk{
  def empty = MeTLDirtyInk("",0L,"",Privacy.NOT_SET,"","",Nil)
}

case class MeTLDirtyText(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences){
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyText => super.matches(o)
    case _ => false
  }
  override def isDirtierFor(other:MeTLCanvasContent) = other match {
    case o:MeTLText => super.matches(o) && o.timestamp < timestamp
    case o:MeTLMultiWordText => super.matches(o) && o.timestamp < timestamp
    case _ => false
  }
  override def alterPrivacy(newPrivacy:Privacy):MeTLDirtyText = copy(privacy=newPrivacy)
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLDirtyText = Stopwatch.time("MeTLDirtyText.adjustTimestamp", {
    copy(timestamp=newTime)
  })
  override def generateNewIdentity(descriptor:String):MeTLDirtyText = copy(identity=genNewIdentity("newMeTLDirtyText:"+descriptor))
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLDirtyText{
  def empty = MeTLDirtyText("",0L,"",Privacy.NOT_SET,"","",Nil)
}

case class MeTLDirtyImage(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyImage => super.matches(o)
    case _ => false
  }
  override def isDirtierFor(other:MeTLCanvasContent) = other match {
    case o:MeTLImage => super.matches(o) && o.timestamp < timestamp
    case _ => false
  }
  override def alterPrivacy(newPrivacy:Privacy):MeTLDirtyImage = copy(privacy=newPrivacy)
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLDirtyImage = Stopwatch.time("MeTLDirtyImage.adjustTimestamp",{
    copy(timestamp=newTime)
  })
  override def generateNewIdentity(descriptor:String):MeTLDirtyImage = copy(identity=genNewIdentity("newMeTLDirtyImage:"+descriptor))
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLDirtyImage{
  def empty = MeTLDirtyImage("",0L,"",Privacy.NOT_SET,"","",Nil)
}

case class MeTLDirtyVideo(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy,override val slide:String,override val identity:String,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLDirtyVideo => super.matches(o)
    case _ => false
  }
  override def isDirtierFor(other:MeTLCanvasContent) = other match {
    case o:MeTLVideo => super.matches(o) && o.timestamp < timestamp
    case _ => false
  }
  override def alterPrivacy(newPrivacy:Privacy):MeTLDirtyVideo = copy(privacy=newPrivacy)
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLDirtyVideo = Stopwatch.time("MeTLDirtyVideo.adjustTimestamp",{
    copy(timestamp=newTime)
  })
  override def generateNewIdentity(descriptor:String):MeTLDirtyVideo = copy(identity=genNewIdentity("newMeTLDirtyVideo:"+descriptor))
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLDirtyVideo{
  def empty = MeTLDirtyVideo("",0L,"",Privacy.NOT_SET,"","",Nil)
}

case class MeTLUndeletedCanvasContent(override val author:String,override val timestamp:Long,override val target:String,override val privacy:Privacy, override val slide:String, override val identity:String,elementType:String,oldElementIdentity:String,newElementIdentity:String,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slide,identity,audiences) {
  override def matches(other:MeTLCanvasContent) = other match {
    case o:MeTLUndeletedCanvasContent => super.matches(o)
    case _ => false
  }
  override def isDirtierFor(other:MeTLCanvasContent) = false
  override def alterPrivacy(newPrivacy:Privacy):MeTLUndeletedCanvasContent = copy(privacy=newPrivacy)
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLUndeletedCanvasContent = Stopwatch.time("MeTLUndeletedCanvasContent.adjustTimestamp",{
    copy(timestamp=newTime)
  })
  override def generateNewIdentity(descriptor:String):MeTLUndeletedCanvasContent = copy(identity=genNewIdentity("newMeTLUndeletedCanvasContent:"+descriptor))
  override def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  override def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  override def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  override def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
}
object MeTLUndeletedCanvasContent{
  def empty = MeTLUndeletedCanvasContent("",0L,"",Privacy.NOT_SET,"","","","","",Nil)
}

case class MeTLCommand(override val author:String,override val timestamp:Long,command:String,commandParameters:List[String],override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLCommand = Stopwatch.time("MeTLCommand.adjustTimestamp",{
    copy(timestamp = newTime)
  })
}
object MeTLCommand{
  def empty = MeTLCommand("",0L,"/No_Command",List.empty[String],Nil)
}

case class MeTLQuiz(override val author:String,override val timestamp:Long,created:Long,question:String,id:String,url:Box[String],imageBytes:Box[Array[Byte]],isDeleted:Boolean,options:List[QuizOption],override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLQuiz = Stopwatch.time("MeTLQuiz.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  def replaceQuestion(newQ:String) = copy(question = newQ)
  def addOption(newO:QuizOption) = copy(options = options ::: List(newO.adjustName(QuizOption.nextName(options))))
  def replaceImage(newImageUrl:Box[String]) = copy(url = newImageUrl,imageBytes = Empty)
  def replaceOption(optionName:String,newText:String) = {
    options.find(o => o.name == optionName).map(or => copy(options = options.filterNot(_ == or) ::: List(or.adjustText(newText)))).getOrElse(copy())
  }
  def removeOption(optionName:String) = {
    options.find(_.name == optionName).map(or => copy(options = options.filterNot(o => o == or).foldLeft(List.empty[QuizOption])((os,o)=> o.adjustName(QuizOption.nextName(os)) :: os))).getOrElse(copy())
  }
  def delete = copy(isDeleted = true)
}
object MeTLQuiz{
  def empty = MeTLQuiz("",0L,0L,"","",Empty,Empty,true,List.empty[QuizOption],Nil)
}

case class MeTLVideoStream(override val author:String,id:String,override val timestamp:Long,url:Box[String],isDeleted:Boolean,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLVideoStream = Stopwatch.time("MeTLVideoStream.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  def delete = copy(isDeleted = true)
}
object MeTLVideoStream{
  def empty = MeTLVideoStream("","",0L,Empty,true,Nil)
}

case class MeTLSubmission(override val author:String,override val timestamp:Long,title:String,slideJid:String,url:String,imageBytes:Box[Array[Byte]] = Empty,blacklist:List[SubmissionBlacklistedPerson] = List.empty[SubmissionBlacklistedPerson], override val target:String = "submission",override val privacy:Privacy = Privacy.PUBLIC,override val identity:String = new Date().getTime.toString,override val audiences:List[Audience] = Nil) extends MeTLCanvasContent(author,timestamp,target,privacy,slideJid,identity){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLSubmission = Stopwatch.time("MeTLSubmission.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  def adjustVisual(xTranslate: Double,yTranslate: Double,xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def alterPrivacy(newPrivacy: com.metl.data.Privacy.Privacy): com.metl.data.MeTLCanvasContent = this
  def generateDirty(dirtyTime: Long): com.metl.data.MeTLCanvasContent = this
  def generateNewIdentity(descriptor: String): com.metl.data.MeTLCanvasContent = this
  def scale(xScale: Double,yScale: Double): com.metl.data.MeTLCanvasContent = this
  def scale(factor: Double): com.metl.data.MeTLCanvasContent = this
  override def equals(other:Any):Boolean = other match {
    case MeTLSubmission(a,ts,t,sid,u,_ib,bl,ta,p,id,aus) => a == author && ts == timestamp && t == title && sid == slideJid && u == url && bl == blacklist && ta == target && p == privacy && id == identity && aus == audiences
    case _ => false
  }
}
object MeTLSubmission{
  def empty = MeTLSubmission("",0L,"","","")
}
case class SubmissionBlacklistedPerson(username:String,highlight:Color)
object SubmissionBlacklistedPerson{
  def empty = SubmissionBlacklistedPerson("",Color.default)
}

case class QuizOption(name:String,text:String,correct:Boolean = false,color:Color = Color.default){
  def adjustName(newName:String) = copy(name = newName,color = QuizOption.colorForName(newName))
  def adjustText(newText:String) = copy(text = newText)
}
object QuizOption{
  def empty = QuizOption("","",false,Color.default)
  def colorForName(optionName:String):Color = optionName.toLowerCase.toArray[Char].reverse.headOption.map(ho => ((ho - 'a').asInstanceOf[Int] % 2) match {
    case 0 => Color(255,255,255,255)
    case 1 => Color(255,70,130,180)
  }).getOrElse(Color(255,255,255,255))
  def numberToName(number:Int):String = {
    if (number > 0){
      val numberPart = number % 26 match {
        case 0 => 26
        case n => n
      }
      val numberRest = number / 26
      val thisChar = (numberPart + 'a' - 1).asInstanceOf[Char]
      if (((number - 1) / 26) > 0)
        (numberToName(numberRest).toArray[Char].toList ::: List(thisChar)).toArray.mkString.toUpperCase
      else
        thisChar.toString.toUpperCase
    } else ""
  }
  def nameToNumber(name:String):Int = {
    name.toLowerCase.toArray[Char].foldLeft(0)((a,i) => (a * 26) + i - 'a' + 1)
  }
  def nextName(options:List[QuizOption] = List.empty[QuizOption]) = {
    options match {
      case l:List[QuizOption] if (l.length > 0) => numberToName(options.map(o => nameToNumber(o.name)).distinct.max + 1)
      case _ => "A"
    }
  }
}

case class MeTLQuizResponse(override val author:String,override val timestamp:Long,answer:String,answerer:String,id:String,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLQuizResponse = Stopwatch.time("MeTLQuizResponse.adjustTimestamp",{
    copy(timestamp = newTime)
  })
}
object MeTLQuizResponse{
  def empty = MeTLQuizResponse("",0L,"","","",Nil)
}

case class MeTLFile(override val author:String, override val timestamp:Long, name:String, id:String, url:Option[String],bytes:Option[Array[Byte]],deleted:Boolean = false,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLFile = Stopwatch.time("MeTLFile.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  def delete:MeTLFile = Stopwatch.time("MeTLFile.delete",{
    copy(deleted = true)
  })
}
object MeTLFile{
  def empty = MeTLFile("",0L,"","",None,None,false,Nil)
}
object MeTLGrade {
  def empty = MeTLGrade("",0L,"","","","")
}
case class MeTLGrade(override val author:String, override val timestamp:Long,id:String,location:String,name:String,description:String,gradeType:MeTLGradeValueType.Value = MeTLGradeValueType.Numeric,visible:Boolean = false,foreignRelationship:Option[Tuple2[String,String]] = None,gradeReferenceUrl:Option[String] = None,numericMaximum:Option[Double] = Some(100.0),numericMinimum:Option[Double] = Some(0.0),override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences){
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLGrade = Stopwatch.time("MeTLGrade.adjustTimestamp",{
    copy(timestamp = newTime)
  })
}
object MeTLGradeValueType extends Enumeration {
  type MeTLGradeValueType = Value
  val Numeric,Boolean,Text = Value
  def parse(input:String):MeTLGradeValueType = {
    input.toLowerCase.trim match {
      case "numeric" => MeTLGradeValueType.Numeric
      case "boolean" => MeTLGradeValueType.Boolean
      case "text" => MeTLGradeValueType.Text
      case _ => MeTLGradeValueType.Numeric
    }
  }
  def print(input:MeTLGradeValueType.Value):String = {
    input match {
      case MeTLGradeValueType.Numeric => "numeric"
      case MeTLGradeValueType.Boolean => "boolean"
      case MeTLGradeValueType.Text => "text"
      case _ => "numeric"
    }
  }
}

trait MeTLGradeValue {
  def getType:MeTLGradeValueType.Value
  def getNumericGrade:Option[Double] = None
  def getTextGrade:Option[String] = None
  def getBooleanGrade:Option[Boolean] = None
  def getComment:Option[String] = None
  def getPrivateComment:Option[String] = None
  def getGradedUser:String
  def getGradeId:String
}
object MeTLNumericGradeValue {
  def empty = MeTLNumericGradeValue("",0L,"","",0.0)
}
case class MeTLNumericGradeValue(override val author:String, override val timestamp:Long,gradeId:String,gradedUser:String,gradeValue:Double,gradeComment:Option[String] = None,gradePrivateComment:Option[String] = None,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences) with MeTLGradeValue {
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLNumericGradeValue = Stopwatch.time("MeTLNumericGradeValue.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def getType:MeTLGradeValueType.Value = MeTLGradeValueType.Numeric
  override def getNumericGrade:Option[Double] = Some(gradeValue)
  override def getGradeId:String = gradeId
  override def getGradedUser:String = gradedUser
  override def getComment:Option[String] = gradeComment
  override def getPrivateComment:Option[String] = gradePrivateComment
}

object MeTLBooleanGradeValue {
  def empty = MeTLBooleanGradeValue("",0L,"","",false)
}
case class MeTLBooleanGradeValue(override val author:String, override val timestamp:Long,gradeId:String,gradedUser:String,gradeValue:Boolean,gradeComment:Option[String] = None,gradePrivateComment:Option[String] = None,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences) with MeTLGradeValue {
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLBooleanGradeValue = Stopwatch.time("MeTLBooleanGradeValue.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def getType:MeTLGradeValueType.Value = MeTLGradeValueType.Boolean
  override def getBooleanGrade:Option[Boolean] = Some(gradeValue)
  override def getGradeId:String = gradeId
  override def getGradedUser:String = gradedUser
  override def getComment:Option[String] = gradeComment
  override def getPrivateComment:Option[String] = gradePrivateComment
}

object MeTLTextGradeValue {
  def empty = MeTLTextGradeValue("",0L,"","","")
}
case class MeTLTextGradeValue(override val author:String, override val timestamp:Long,gradeId:String,gradedUser:String,gradeValue:String,gradeComment:Option[String] = None,gradePrivateComment:Option[String] = None,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences) with MeTLGradeValue {
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):MeTLTextGradeValue = Stopwatch.time("MeTLTextGradeValue.adjustTimestamp",{
    copy(timestamp = newTime)
  })
  override def getType:MeTLGradeValueType.Value = MeTLGradeValueType.Text
  override def getTextGrade:Option[String] = Some(gradeValue)
  override def getGradeId:String = gradeId
  override def getGradedUser:String = gradedUser
  override def getComment:Option[String] = gradeComment
  override def getPrivateComment:Option[String] = gradePrivateComment
}

object ForumPost {
  def empty = ForumPost("",0L,"",None,"","",Nil)
}

case class ForumPost(override val author:String,override val timestamp:Long,identity:String,inResponseTo:Option[String],slideId:String,text:String,override val audiences:List[Audience] = Nil) extends MeTLStanza(author,timestamp,audiences) {
  override def adjustTimestamp(newTime:Long = new java.util.Date().getTime):ForumPost = copy(timestamp = newTime)
}
