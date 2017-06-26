package com.metl.comet

import com.metl.data._
import com.metl.utils._
import com.metl.liftExtensions._
import net.liftweb._
import net.liftweb.json._
import common._
import http._
import net.liftweb.http.js.JsCmds.SetHtml
import util._
import Helpers._
import HttpHelpers._
import actor._

import scala.xml._
import com.metl.model._
import SHtml._
import org.fluttercode.datafactory.impl._
import js._
import JsCmds._
import JE._
import net.liftweb.http.js.jquery.JqJsCmds._
import net.liftweb.http.js.jquery.JqJE._

import scala.collection.mutable.Queue

trait TrainingBlock
case class TrainingInstruction(content:NodeSeq) extends TrainingBlock
case class TrainingControl(label:String,behaviour:()=>JsCmd) extends TrainingBlock {
  var isActioned = false
  var progressMarker = 0
  var maxProgress = 0
  def actioned = isActioned = true
  def progress = progressMarker = progressMarker + 1
}
case class TrainingPage(title:NodeSeq,blurb:NodeSeq,blocks:Seq[TrainingBlock],onLoad:Box[JsCmd]) {
  def receiveStanza(s:MeTLStanza):Unit = ()
}

case object SimulatorTick

case class ScanDirection(label:String)
object ScanDirections {
  val Above = ScanDirection("above")
  val Below = ScanDirection("below")
  val Leftwards = ScanDirection("leftwards")
  val Rightwards = ScanDirection("rightwards")
}
object Dimensions {
  val lineHeight = 200
  val horizontalSpace = 30
}

import ScanDirections._


trait SimulatedActivity
case class Watching(ticks:Int) extends SimulatedActivity
case class Scribbling(what:List[Char]) extends SimulatedActivity

case class Highlight(selector:String)
case class Flash(selector:String)
case class ShowClick(selector:String)

case class ClaimedArea(left:Double,top:Double,right:Double,bottom:Double,width:Double)
case class SimulatedUser(name:String,claim:ClaimedArea,focus:Point,attention:ScanDirection,intention:String,activity:SimulatedActivity,history:List[SimulatedActivity]){
  def pan(x:Int,y:Int = 0) = copy(focus = Point(focus.x + x,focus.y + y,0))
}
case class TrainingManual(actor:TrainerActor) {
  import Dimensions._
  val namer = new DataFactory
  def el(label:String,content:String) = Elem.apply(null,label,scala.xml.Null,scala.xml.TopScope,Text(content))
  def p(content:String) = TrainingInstruction(el("p",content))
  def makeStudent(intent:String) = TrainingControl(
    "Bring in a %s student".format(intent),() => {
      actor.users = SimulatedUser("%s %s".format(namer.getFirstName, namer.getLastName),ClaimedArea(0,0,0,lineHeight,0),Point(0,0,0),Below,"benign",Watching(0),List.empty[SimulatedActivity]) :: actor.users
    })
  val pages:List[TrainingPage] = List(
    TrainingPage(Text("Exercise 1"),
      Text("The teaching space"),
      List(
        p("This space is a whiteboard on which you and your class can write."),
        p("Like a slide deck or a PowerPoint presentation, it supports multiple pages."),
        TrainingControl(
          "Show me the pages",
          () => actor ! Flash("#thumbsColumn")
        ),
        p("You may choose whether your class can move freely between these."),
        TrainingControl("How do I move between pages?",
          () => actor ! Flash("#slideControls")
        ),
        p("Only the author of the conversation can add pages."),
        TrainingControl(
          "Show me how",
          () => {
            Schedule.schedule(actor,Flash("#addSlideButton"),1000)
            actor ! Highlight("#slideControls")
          }
        ),
        p("In the next exercise, we'll do some work on the pages"),
        TrainingControl(
          "Take me there",
          () => actor ! pages(1)
        )),
      Full(
        Call("Trainer.clearTools").cmd
      )
    ),
    TrainingPage(Text("Exercise 2"),
      Text("Sharing an open space"),
      List(
        p("In this trainer you can bring virtual students into your classroom.  These students will stay with you during your training session.  Bring one in now."),
        makeStudent("benign"),
        p("The students you create can work anywhere, even outside of where you are currently looking."),
        p("To observe all their work, set your camera to include all content no matter where it appears."),
        TrainingControl(
          "Show me how to watch everything",
          () => {
            Schedule.schedule(actor,ShowClick("#zoomToFull"),1000)
            actor ! ShowClick("#zoomMode")
          }
        ),
        p("If you just want to concentrate on your own work, set your camera not to move automatically."),
        TrainingControl(
          "Show me how to stop the camera moving",
          () => {
            Schedule.schedule(actor,ShowClick("#zoomToCurrent"),1000)
            actor ! ShowClick("#zoomMode")
          }
        ),
        p("Now let's make some of your own content."),
        TrainingControl(
          "Show me the rest of the tools",
          () => actor ! pages(2)
        ),
        TrainingControl(
          "Show me exercise 1 again",
          () => actor ! pages(0)
        )
      ),
      Full(
        Call("Trainer.clearTools").cmd &
          Call("Trainer.highlight","#toolsColumn").cmd &
          Call("Trainer.hide",".permission-states").cmd &
          Call("Trainer.hide","#floatingToggleContainer").cmd &
          Call("Trainer.hide",".meters").cmd
      )
    ),
    TrainingPage(Text("Exercise 3"),
      Text("Your creative space"),
      List(
        p("You have control over whether your content appears to other users."),
        TrainingControl(
          "Which controls do that?",
          () => {
            actor ! Highlight("#toolsColumn")
            actor ! Flash(".permission-states")
          }
        ),
        p("Classroom spaces, the device you're using and network speed can affect whether the whiteboard works well for you."),
        TrainingControl(
          "How can I tell if there's a problem?",
          () => actor ! Highlight(".meters")
        ),
        p("You can add several kinds of content to the space.  Your selection of Public versus Private at the time you add new content will determine whether that content is visible to others.  It is always visible to you, and you can change your mind later."),
        TrainingControl(
          "Show me how to add content",
          () => {
            actor ! Flash("#drawMode")
            actor ! Flash("#insertText")
            actor ! Flash("#insertMode")
          }
        ),
        p("Once you have added content, you may need to move it, resize it, hide or show it."),
        TrainingControl(
          "Show me how to modify existing content",
          () => {
            actor ! ShowClick("#selectMode")
          }
        ),
        TrainingControl(
          "Show me exercise 2 again",
          () => actor ! pages(1)
        )
      ),
      Full(Call("Trainer.showTools").cmd)
    )
  )
}

class TrainerActor extends StronglyTypedJsonActor with Logger {
  import Dimensions._
  implicit val formats = net.liftweb.json.DefaultFormats
  var manual = new TrainingManual(this)
  var users = List.empty[SimulatedUser]
  def registerWith = MeTLActorManager
  var currentPage:TrainingPage = manual.pages(0)
  protected var currentConversation:Box[Conversation] = Empty
  protected var currentSlide:Box[String] = Empty
  protected val username = Globals.currentUser.is
  protected lazy val serverConfig = ServerConfiguration.default
  protected lazy val server = serverConfig.name
  protected var triggers = List.empty[StanzaTrigger]

  override lazy val functionDefinitions = List.empty[ClientSideFunction]

  val rand = new scala.util.Random
  val sum = 0.0
  def findFreeSpace(width:Double,direction:ScanDirection,claims:List[ClaimedArea]):ClaimedArea = {
    direction match {
      case Above => claims.sortBy(_.top).head match {
        case p => ClaimedArea(p.left,p.top - lineHeight,p.left + width + horizontalSpace,p.top,width)
      }
      case Below => claims.sortBy(_.bottom).last match {
        case p => ClaimedArea(p.left,p.bottom,p.left + width,p.bottom + lineHeight,width)
      }
      case Leftwards => claims.sortBy(c => c.left).head match {
        case p => ClaimedArea(p.left - (width + horizontalSpace),p.top,p.left,p.bottom,width)
      }
      case Rightwards => claims.sortBy(c => c.right).last match {
        case p => ClaimedArea(p.right,p.top,p.right + width,p.bottom,width)
      }
    }
  }

  override def lowPriority  = {
    case Highlight(selector) => partialUpdate(Call("Trainer.highlight",JString(selector)).cmd)
    case Flash(selector) => partialUpdate(Call("Trainer.flash",JString(selector)).cmd)
    case ShowClick(selector) => partialUpdate(Call("Trainer.showClick",JString(selector)).cmd)
    case p:TrainingPage => {
      currentPage = p
      reRender(true)
    }
    case SimulatorTick => {
      var furtherClaimsAllowed = true
      users = users.map {
        case u@SimulatedUser(name,claim,focus,_,intention,_,history) if history.size >= name.size => u
        case u@SimulatedUser(name,claim,focus,_,intention,Watching(ticks),history) => rand.nextInt(2) match {
          case 1 if furtherClaimsAllowed => {
            val width = name.length * Alphabet.averageWidth
            val direction = rand.nextInt(10) match {
              case 1 => Above
              case 2 | 3 => Rightwards
              case 4 | 5 | 6 | 7 => Below
              case _ => Leftwards
            }
            val claim = findFreeSpace(width, direction,users.map(_.claim))
            furtherClaimsAllowed = false
            u.copy(activity=Scribbling(name.toLowerCase.toList), focus=Point(claim.left,claim.top,0), claim=claim, attention=direction)
          }
          case _ => u.copy(activity = Watching(ticks + 1))
        }
        case u@SimulatedUser(name,claim,focus,_,intention,Scribbling(Nil),history) => u.copy(activity = Watching(0))
        case u@SimulatedUser(name,claim,focus,_,intention,Scribbling(h :: t),history) if h == ' ' => u.pan(Alphabet.space.width).copy(activity = Scribbling(t),history = Scribbling(List(h)) :: u.history)
        case u@SimulatedUser(name,claim,focus,_,intention,Scribbling(h :: t),history) => Alphabet.geometry(h,focus) match {
          case g => {
            currentSlide.map(slide => {
              val room = MeTLXConfiguration.getRoom(slide,server)
              g.strokes.map(geometry => room ! LocalToServerMeTLStanza(MeTLInk(serverConfig,name,new java.util.Date().getTime,sum,sum,
                geometry,intention match {
                  case "benign" => Color(255,255,0,0)
                  case "malicious" => Color(255,0,0,255)
                  case _ => Color(255,0,255,0)
                },2.0,false,"presentationSpace",Privacy.PUBLIC,slide.toString,nextFuncName)))
            })
            u.pan(g.width).copy(activity = Scribbling(t),history = Scribbling(List(h)) :: u.history)
          }
        }
      }
      partialUpdate(Call("Trainer.simulatedUsers",Extraction.decompose(users)).cmd)
      Schedule.schedule(this,SimulatorTick,1000)
    }
  }

  class StanzaTrigger(val actOn:(MeTLStanza) => MeTLStanza = (s:MeTLStanza) => s) {
    override def equals(other:Any):Boolean = {
      other match {
        case ost:StanzaTrigger => ost.actOn == actOn
        case _ => false
      }
    }
    override def hashCode = actOn.hashCode
  }

  val humanAuthor = "public"
  def isHuman(stanza:MeTLStanza):Boolean = {humanAuthor.equals(stanza.author)}

  val humanStanzas = new Queue[MeTLStanza]()
  val simulatedStanzas = new Queue[MeTLStanza]()
  def enqueueStanza(stanza: MeTLStanza, queue: Queue[MeTLStanza]):MeTLStanza = {
    queue += stanza
    println("Queue(" + queue.size + "): " + queue.toString)
    stanza
  }

  private def logStanza(s: MeTLStanza, prefix:String):MeTLStanza = {
    println(prefix + " (" + "author: " + s.author + ", " + "timestamp: " + s.timestamp + ")")
    s
  }

  override def localSetup = {
    super.localSetup
    val newConversation = serverConfig.createConversation("a practice conversation",username)
    val newSlide = newConversation.slides(0)
    val slide = newSlide.id.toString
    currentConversation = Full(newConversation)
    currentSlide = Full(slide)

    triggers = List(
      new StanzaTrigger((stanza:MeTLStanza) => {
        if(isHuman(stanza)) {
          logStanza(stanza,"Human")
          enqueueStanza(stanza,humanStanzas)
        }
        else {
          logStanza(stanza,"Simulated")
          enqueueStanza(stanza,simulatedStanzas)
        }
      }),
      new StanzaTrigger((stanza:MeTLStanza) => {logStanza(stanza, "All")})
    )
    serverConfig.getMessageBus(new MessageBusDefinition(newConversation.jid.toString, "unicastBackToOwner",
      (s:MeTLStanza) => { triggers.foreach(t => t.actOn(s))}))

    Schedule.schedule(this,SimulatorTick,500)
  }

  def blockMarkup:NodeSeq = currentPage.blocks.map(b => {
    val id = nextFuncName
    NodeSeq.fromSeq(List(
      b match {
        case c:TrainingControl => <div class="actioned">{
          NodeSeq.fromSeq(List(
            c.isActioned match {
              case true => <input type="checkbox" id={id} disabled="true" checked="checked" />
              case _ => <input type="checkbox" id={id} disabled="true" />
            },
            <label for={id}><span class="icon-txt"></span></label>))
        }</div>
        case _ => <span />
      },
      <div class="control">{
        b match {
          case c:TrainingControl =>
            ajaxButton(c.label,() => {
              c.actioned
              c.behaviour()
              SetHtml("exerciseControls",blockMarkup)
            },"class" -> "active")
          case c:TrainingInstruction => c.content
        }
      }</div>
    ))}).reduce(_ ++ _)
  override def render = "#exerciseTitle *" #> currentPage.title &
  "#exerciseBlurb *" #> currentPage.blurb &
  "#exerciseControls *" #> blockMarkup &
  "#scriptContainer *" #> (for {
    c <- currentConversation
    s <- currentSlide
  } yield Script(Call("Trainer.simulationOn",c.jid,s,currentPage.onLoad.map(AnonFunc(_)).openOr(JsNull)).cmd))
}

case class Glyph(width:Int,strokes:List[List[Point]])
object Alphabet {
  implicit val formats = net.liftweb.json.DefaultFormats
  val space = Glyph(50,Nil)
  def geometry(c:Char,p:Point) = {
    c match {
      case char if char >= 'a' && char <= 'z' => glyphs(char - 'a') match {
        case g => g.copy(strokes = g.strokes.map(stroke => stroke.map(point => Point(point.x + p.x,point.y + p.y,point.thickness))))
      }
      case _ => space
    }
  }
  val averageWidth = glyphs.map(_.width).reduceLeft(_ + _) / glyphs.size
  lazy val glyphs:List[Glyph] = (List(2,1,1,2,2,3,1,3,1,1,2,1,1,1,1,1,2,1,1,2,1,1,1,2,1,1).foldLeft((pointJ.extract[List[List[Int]]],List.empty[List[List[Point]]])){
    case ((source,acc),i) => source.splitAt(i) match {
      case (strokes,t) => (t, strokes.map(points => points.grouped(3).map {
        case List(x,y,p) => Point(x,y,p)
      }.toList) :: acc)
    }
  } match {
    case (_,absoluteChars) => absoluteChars.collect {
      case absoluteStrokes if !absoluteStrokes.isEmpty => {
        val absolutePoints = absoluteStrokes.flatten
        val xs = absolutePoints.map(_.x)
        val ys = absolutePoints.map(_.y)
        val offset = Point(xs.min,ys.min,0)
        Glyph((xs.max - xs.min).intValue,absoluteStrokes.map(stroke => {
          stroke.map(abs => Point(abs.x - offset.x,abs.y - offset.y,0)).toList
        }))
      }
    }
  }).reverse
  lazy val pointJ = net.liftweb.json.parse("[[55.354,195.197,128,48.362,173.055,128,48.362,167.228,128,48.362,160.819,128,50.11,154.409,128,51.858,148,128,55.354,141.008,128,62.346,129.354,128,65.843,125.858,128,68.756,123.528,128,71.669,122.362,128,74.583,124.11,128,77.496,127.606,128,79.244,132.85,128,82.157,139.843,128,84.488,149.165,128,86.819,160.236,128,87.402,172.472,128,88.567,185.291,128,87.984,194.614,128,87.402,202.189,128,87.402,207.433,128,86.819,210.929,128,86.236,211.512,128,85.654,211.512,128,85.654,211.512,128],[43.701,188.787,128,62.929,184.126,128,68.756,182.961,128,75.165,180.63,128,82.74,177.717,128,90.315,174.22,128,96.724,170.724,128,102.551,166.646,128,108.961,164.315,128,108.961,164.315,128],[172.472,150.913,128,171.89,169.559,128,173.638,174.22,128,175.969,179.465,128,176.551,183.543,128,177.134,186.457,128,177.717,189.37,128,177.717,191.118,128,177.134,191.118,128,176.551,191.118,128,175.969,187.622,128,174.803,182.378,128,173.638,174.803,128,173.055,164.315,128,173.638,152.661,128,174.22,141.008,128,176.551,131.685,128,180.047,125.276,128,184.126,120.031,128,189.37,117.701,128,194.031,117.701,128,198.11,120.614,128,202.189,124.11,128,205.102,130.52,128,208.016,137.512,128,208.016,145.669,128,206.85,153.827,128,204.52,162.567,128,201.024,168.976,128,196.945,174.22,128,192.866,177.134,128,182.378,178.299,128,175.386,176.551,128,173.055,175.386,128,171.89,174.803,128,171.89,174.22,128,174.22,173.055,128,177.717,171.307,128,181.795,170.724,128,186.457,168.394,128,191.118,167.228,128,196.945,166.646,128,203.354,166.646,128,209.181,167.811,128,214.425,170.142,128,218.504,173.055,128,220.835,177.134,128,222.583,181.795,128,223.165,186.457,128,219.087,192.866,128,215.008,194.614,128,209.181,194.614,128,201.606,191.701,128,194.031,188.787,128,187.039,185.291,128,180.047,183.543,128,170.142,182.378,128,170.142,182.378,128],[314.646,151.496,128,296.583,146.252,128,294.252,146.252,128,292.504,146.252,128,290.173,146.835,128,287.843,148,128,284.929,149.165,128,282.598,150.913,128,280.268,153.244,128,278.52,155.575,128,277.354,158.488,128,276.189,163.732,128,276.189,170.142,128,276.189,177.134,128,276.772,183.543,128,278.52,189.37,128,281.433,194.614,128,286.094,198.11,128,291.339,199.858,128,298.331,198.693,128,305.323,195.78,128,313.48,191.701,128,320.472,185.874,128,320.472,185.874,128],[389.811,157.323,128,392.724,174.803,128,394.472,181.213,128,397.386,187.622,128,399.134,194.031,128,400.882,199.858,128,402.63,204.52,128,403.213,207.433,128,402.047,207.433,128,402.047,203.354,128,402.047,203.354,128],[363.008,166.063,128,378.74,156.157,128,382.819,154.992,128,387.48,153.827,128,392.142,153.827,128,397.386,154.409,128,402.047,155.575,128,407.874,158.488,128,413.118,162.567,128,418.362,168.394,128,422.441,174.803,128,425.937,180.63,128,428.268,185.874,128,428.85,191.118,128,427.685,195.78,128,424.772,200.441,128,420.693,203.937,128,415.449,206.268,128,402.047,209.764,128,395.055,210.346,128,388.063,211.512,128,382.236,211.512,128,371.165,210.346,128,370,209.181,128,370,209.181,128],[531.402,151.496,128,512.173,147.417,128,508.677,147.417,128,504.598,147.417,128,499.937,148.583,128,495.858,150.331,128,491.78,152.079,128,488.283,153.827,128,484.787,156.74,128,482.457,160.819,128,481.291,165.48,128,480.126,170.142,128,480.709,174.803,128,482.457,180.047,128,485.37,185.291,128,489.449,190.535,128,494.11,195.197,128,499.354,198.693,128,505.764,201.024,128,512.173,201.606,128,519.748,200.441,128,526.157,197.528,128,532.567,194.031,128,538.394,191.118,128,542.472,187.622,128,545.386,185.874,128,545.386,185.874,128],[484.205,184.126,128,502.85,185.291,128,506.346,184.126,128,511.591,182.961,128,518.583,177.717,128,518.583,177.717,128],[607.15,157.323,128,615.307,175.969,128,617.638,182.378,128,619.386,191.118,128,621.134,200.441,128,621.717,223.165,128,619.969,222.583,128,620.551,220.252,128,620.551,220.252,128],[590.252,156.74,128,608.898,151.496,128,614.724,150.331,128,620.551,149.165,128,626.961,146.835,128,633.953,143.339,128,641.528,139.26,128,648.52,135.181,128,654.346,132.268,128,657.843,130.52,128,660.173,130.52,128,660.173,129.354,128,660.173,129.354,128],[599.575,185.291,128,618.22,180.63,128,625.213,178.882,128,632.787,176.551,128,639.78,173.638,128,646.189,170.724,128,653.181,167.811,128,658.425,166.063,128,663.087,164.898,128,663.087,164.898,128],[761.559,159.654,128,742.331,150.913,128,737.669,150.331,128,733.591,150.331,128,730.094,149.748,128,727.181,150.331,128,724.268,151.496,128,720.772,152.661,128,717.858,153.827,128,715.528,155.575,128,713.78,157.906,128,712.031,161.984,128,709.701,167.228,128,707.953,174.22,128,705.622,181.795,128,704.457,190.535,128,703.874,198.693,128,704.457,206.268,128,705.622,212.677,128,707.953,218.504,128,711.449,223.165,128,714.945,226.079,128,719.606,227.244,128,723.685,227.244,128,728.929,226.661,128,734.173,224.331,128,738.835,220.835,128,742.913,216.756,128,746.409,212.677,128,748.74,208.598,128,750.488,204.52,128,751.654,201.024,128,751.654,198.693,128,751.071,196.945,128,750.488,196.362,128,749.323,196.362,128,748.157,196.945,128,746.992,197.528,128,746.409,198.693,128,746.409,199.276,128,746.409,199.858,128,746.409,200.441,128,746.409,201.024,128,747.575,201.606,128,748.157,201.606,128,748.74,202.189,128,749.323,202.772,128,749.906,202.772,128,751.071,202.772,128,751.654,202.772,128,752.236,203.354,128,752.819,203.354,128,752.819,202.772,128,752.819,203.354,128,753.402,209.181,128,754.567,212.677,128,755.15,216.756,128,756.898,222.583,128,757.48,227.244,128,758.063,232.488,128,759.228,237.732,128,759.811,242.976,128,760.976,245.307,128,758.063,244.724,128,758.063,244.724,128],[853.622,149.748,128,858.866,224.331,128,858.866,224.331,128],[829.732,206.85,128,847.795,200.441,128,856.535,198.11,128,865.276,195.78,128,872.85,193.449,128,880.425,191.701,128,886.835,189.953,128,891.496,188.205,128,895.575,186.457,128,897.323,185.291,128,899.071,187.039,128,899.071,187.039,128],[888,145.669,128,888,167.811,128,888,176.551,128,888.583,185.874,128,889.165,195.197,128,889.165,205.102,128,889.748,215.591,128,889.748,226.079,128,889.748,234.819,128,890.913,241.228,128,893.244,244.724,128,893.244,244.724,128],[969.575,142.173,128,976.567,161.402,128,978.315,170.142,128,979.48,180.63,128,980.646,190.535,128,983.559,219.087,128,984.142,223.748,128,984.142,223.748,128],[1099.512,159.654,128,1106.504,177.134,128,1107.669,185.291,128,1108.252,194.614,128,1108.835,202.189,128,1108.835,210.346,128,1107.669,218.504,128,1105.921,225.496,128,1103.591,230.74,128,1100.677,233.654,128,1096.598,234.819,128,1086.693,232.488,128,1080.866,229.575,128,1056.394,216.173,128,1052.898,213.843,128,1050.567,212.094,128,1047.654,211.512,128,1047.654,211.512,128],[1179.339,147.417,128,1184.583,168.394,128,1185.748,177.134,128,1186.913,187.622,128,1187.496,196.945,128,1188.079,205.102,128,1188.661,212.677,128,1189.244,219.087,128,1189.244,222.583,128,1188.661,224.913,128,1187.496,224.913,128,1187.496,221.417,128,1187.496,217.921,128,1187.496,215.008,128,1189.827,212.677,128,1189.827,212.677,128],[1228.866,142.756,128,1211.386,156.74,128,1206.724,161.402,128,1202.646,166.646,128,1193.323,178.299,128,1186.913,188.787,128,1185.748,192.283,128,1186.331,195.197,128,1188.661,195.197,128,1191.575,194.031,128,1195.654,192.866,128,1199.732,191.701,128,1202.646,191.118,128,1205.559,191.118,128,1208.472,191.701,128,1210.803,193.449,128,1213.134,195.78,128,1216.047,198.11,128,1218.378,200.441,128,1221.874,202.772,128,1225.953,204.52,128,1229.449,206.85,128,1232.945,209.181,128,1237.024,210.929,128,1241.102,210.929,128,1241.102,210.929,128],[1309.276,155.575,128,1312.189,177.717,128,1311.024,187.039,128,1310.441,196.945,128,1309.276,206.268,128,1308.11,213.26,128,1307.528,219.087,128,1307.528,223.165,128,1307.528,226.079,128,1306.945,227.827,128,1307.528,228.409,128,1308.11,228.409,128,1308.693,228.409,128,1315.102,223.165,128,1319.764,221.417,128,1326.173,219.669,128,1332.583,217.921,128,1341.323,216.173,128,1350.063,213.843,128,1358.803,210.929,128,1365.213,208.598,128,1369.874,206.268,128,1371.622,204.52,128,1370.457,202.189,128,1370.457,202.189,128],[68.173,350.772,128,67.008,329.213,128,68.173,322.803,128,69.339,315.811,128,71.087,308.236,128,72.252,301.827,128,73.417,294.835,128,75.165,289.008,128,76.913,283.764,128,78.661,280.268,128,80.409,279.102,128,82.157,278.52,128,84.488,279.685,128,86.819,281.433,128,89.732,284.929,128,91.48,288.425,128,93.811,293.087,128,96.142,297.748,128,98.472,302.409,128,100.803,305.906,128,102.551,308.819,128,103.717,310.567,128,104.299,311.732,128,104.882,311.732,128,106.047,311.732,128,107.213,307.654,128,113.039,292.504,128,115.953,284.929,128,118.866,277.937,128,122.945,272.693,128,126.441,268.031,128,129.354,265.701,128,132.85,265.701,128,136.346,267.449,128,139.26,270.945,128,142.173,275.606,128,144.504,282.598,128,145.669,290.173,128,145.669,300.079,128,145.087,311.732,128,143.339,323.969,128,142.173,335.039,128,141.008,344.362,128,139.843,350.189,128,139.843,353.685,128,140.425,354.268,128,142.756,351.937,128,142.756,351.937,128],[228.409,354.85,128,223.165,336.205,128,224.331,329.213,128,224.331,321.055,128,224.331,312.315,128,223.748,305.323,128,223.748,298.913,128,223.165,293.087,128,223.165,288.425,128,223.165,286.094,128,223.748,284.929,128,224.331,284.929,128,225.496,286.677,128,227.244,289.008,128,230.157,293.087,128,234.236,297.165,128,237.732,302.409,128,241.811,307.071,128,245.89,312.898,128,256.961,327.465,128,262.787,335.039,128,279.102,353.685,128,284.346,357.181,128,289.008,359.512,128,292.504,360.094,128,295.417,358.346,128,287.843,294.835,128,286.094,283.181,128,284.346,273.858,128,283.181,265.118,128,282.598,258.709,128,282.598,255.213,128,282.598,253.465,128,283.764,253.465,128,285.512,258.126,128,289.008,263.37,128,291.921,270.945,128,291.921,270.945,128],[415.449,307.071,128,396.803,304.157,128,392.142,303.575,128,387.48,302.992,128,382.236,304.157,128,378.157,304.74,128,373.496,306.488,128,368.835,308.819,128,364.756,312.898,128,361.843,316.976,128,359.512,322.22,128,358.346,326.882,128,357.764,331.543,128,357.764,336.205,128,359.512,343.197,128,361.843,350.189,128,364.756,356.598,128,367.669,362.425,128,371.748,367.669,128,376.992,371.748,128,383.402,373.496,128,390.394,373.496,128,397.386,371.748,128,403.795,368.252,128,420.11,353.102,128,423.606,347.276,128,412.535,303.575,128,407.874,300.661,128,403.795,300.079,128,399.134,300.079,128,395.638,301.244,128,392.142,303.575,128,389.228,307.071,128,386.315,312.315,128,383.402,318.142,128,379.906,325.134,128,379.906,325.134,128],[526.157,294.835,128,522.079,316.976,128,524.409,323.969,128,527.323,331.543,128,530.819,339.701,128,533.15,347.276,128,535.48,353.685,128,537.228,358.929,128,538.976,362.425,128,540.142,366.504,128,524.409,348.441,128,522.661,344.945,128,520.913,340.866,128,519.165,336.205,128,516.835,332.126,128,515.087,328.047,128,513.339,323.969,128,512.173,319.307,128,511.591,314.646,128,511.591,309.984,128,511.591,305.323,128,511.591,302.409,128,512.756,298.913,128,514.504,295.417,128,516.835,291.921,128,519.165,288.425,128,522.079,285.512,128,524.992,283.181,128,528.488,281.433,128,533.15,280.268,128,538.394,279.685,128,543.055,280.85,128,547.717,282.016,128,552.378,284.346,128,557.039,287.843,128,561.118,292.504,128,565.197,296.583,128,568.11,300.661,128,570.441,303.575,128,572.189,305.906,128,572.772,308.236,128,572.772,309.984,128,572.189,311.732,128,571.024,312.898,128,569.276,312.898,128,566.945,314.063,128,564.614,314.646,128,561.118,315.811,128,556.457,317.559,128,551.795,319.307,128,547.134,321.638,128,542.472,324.551,128,537.811,327.465,128,534.315,329.795,128,531.402,331.543,128,529.071,333.291,128,529.071,333.291,128],[673.575,293.087,128,611.811,343.78,128,655.512,371.165,128,673.575,349.606,128,677.654,336.205,128,678.819,329.795,128,678.819,322.803,128,677.071,315.228,128,670.661,300.661,128,666.583,295.417,128,661.339,290.756,128,656.094,286.677,128,649.102,283.181,128,642.693,282.016,128,636.866,283.181,128,632.787,285.512,128,628.126,290.756,128,628.126,290.756,128],[664.835,347.276,128,686.394,351.937,128,691.055,354.268,128,696.299,356.598,128,701.543,359.512,128,706.205,363.008,128,711.449,367.087,128,714.945,371.165,128,718.441,374.661,128,721.937,376.992,128,724.85,378.74,128,724.85,378.74,128],[770.882,317.559,128,777.291,336.205,128,779.039,343.197,128,780.205,350.772,128,781.953,358.346,128,781.37,364.756,128,781.37,370,128,780.787,372.331,128,779.039,372.913,128,777.291,371.165,128,774.378,365.339,128,770.882,358.929,128,767.969,350.772,128,767.969,296,128,771.465,289.591,128,775.543,284.929,128,779.622,282.598,128,784.283,282.016,128,787.78,283.181,128,790.693,287.843,128,792.441,292.504,128,793.024,298.913,128,792.441,305.906,128,790.693,314.063,128,788.362,322.803,128,786.031,330.378,128,782.535,336.787,128,779.039,342.614,128,776.126,346.693,128,772.63,349.024,128,770.299,350.189,128,768.551,350.772,128,767.969,349.606,128,767.969,349.024,128,770.299,346.11,128,773.213,343.78,128,777.874,342.031,128,782.535,340.866,128,787.78,340.866,128,793.606,342.031,128,800.016,344.945,128,807.008,348.441,128,825.654,362.425,128,830.898,365.921,128,835.559,367.669,128,841.969,368.252,128,841.969,368.252,128],[939.276,322.22,128,931.701,302.992,128,929.953,300.079,128,927.039,298.331,128,924.126,297.748,128,921.213,297.748,128,917.717,298.331,128,914.22,300.079,128,910.724,302.409,128,907.811,305.906,128,905.48,309.402,128,903.15,313.48,128,901.984,317.559,128,901.402,322.22,128,920.63,340.866,128,927.039,344.945,128,932.866,349.024,128,938.11,352.52,128,942.189,356.598,128,946.268,361.26,128,949.181,366.504,128,950.929,372.331,128,951.512,377.575,128,950.929,381.654,128,948.598,386.315,128,945.102,389.811,128,939.276,392.724,128,932.283,394.472,128,924.126,395.055,128,917.134,394.472,128,909.559,392.142,128,903.15,389.811,128,896.157,388.063,128,896.157,388.063,128],[1055.228,311.15,128,1062.803,328.047,128,1065.717,336.787,128,1072.709,366.504,128,1077.953,391.559,128,1077.953,391.559,128],[1009.78,321.055,128,1032.504,311.732,128,1041.827,309.984,128,1052.898,308.819,128,1064.551,307.654,128,1076.787,305.906,128,1087.858,304.157,128,1097.764,302.992,128,1107.087,301.827,128,1115.244,300.079,128,1121.654,298.331,128,1121.654,298.331,128],[1185.748,329.213,128,1188.661,349.024,128,1190.992,354.85,128,1193.323,360.677,128,1196.236,367.669,128,1199.732,374.079,128,1203.811,379.906,128,1208.472,385.15,128,1214.299,388.646,128,1220.126,390.976,128,1225.953,389.811,128,1230.614,387.48,128,1234.693,383.402,128,1237.606,376.992,128,1240.52,367.669,128,1241.685,358.346,128,1241.685,349.024,128,1239.354,331.543,128,1237.024,323.969,128,1234.693,315.228,128,1231.78,308.236,128,1229.449,301.827,128,1227.701,297.748,128,1225.953,294.835,128,1225.953,293.087,128,1225.953,292.504,128,1226.535,291.921,128,1227.701,290.756,128,1227.701,290.756,128],[1305.78,298.913,128,1325.008,311.732,128,1330.252,318.724,128,1355.307,368.252,128,1357.055,372.913,128,1357.638,376.409,128,1358.22,377.575,128,1357.638,377.575,128,1357.638,375.244,128,1357.638,373.496,128,1357.638,371.748,128,1357.638,370,128,1357.055,368.835,128,1356.472,367.669,128,1355.89,367.087,128,1355.89,365.921,128,1355.89,364.173,128,1355.89,361.26,128,1356.472,357.764,128,1357.638,354.268,128,1359.386,349.606,128,1362.299,343.197,128,1366.378,335.039,128,1372.205,324.551,128,1378.614,312.898,128,1384.441,301.827,128,1390.268,290.756,128,1395.512,281.433,128,1399.591,273.858,128,1402.504,268.031,128,1403.669,264.535,128,1403.669,262.787,128,1401.921,264.535,128,1397.26,268.031,128,1397.26,268.031,128],[120.031,431.181,128,137.512,502.85,128,145.087,464.976,128,148.583,455.654,128,150.913,454.488,128,152.079,454.488,128,154.992,457.984,128,155.575,460.315,128,156.74,462.646,128,158.488,465.559,128,159.654,468.472,128,160.819,471.386,128,162.567,474.882,128,164.898,478.378,128,167.228,482.457,128,170.142,485.953,128,172.472,488.866,128,175.386,491.197,128,177.717,493.528,128,180.63,494.693,128,182.961,495.276,128,185.291,495.276,128,186.457,494.693,128,187.039,493.528,128,187.622,492.362,128,187.622,490.031,128,187.622,488.283,128,186.457,485.37,128,185.291,482.457,128,184.126,477.795,128,182.378,472.551,128,181.213,464.976,128,179.465,456.819,128,178.882,448.661,128,178.299,441.087,128,178.299,434.094,128,179.465,428.85,128,180.63,423.606,128,181.795,420.11,128,183.543,417.78,128,185.291,416.614,128,188.205,416.031,128,191.118,416.031,128,193.449,416.031,128,193.449,416.031,128],[256.378,448.079,128,272.11,459.15,128,278.52,463.811,128,284.346,469.638,128,290.173,474.882,128,296.583,479.543,128,302.409,485.37,128,308.236,491.197,128,313.48,496.441,128,316.976,500.52,128,319.307,502.85,128,321.055,502.85,128,316.976,500.52,128,316.976,500.52,128],[323.969,454.488,128,304.157,462.646,128,300.079,464.976,128,296,467.89,128,291.921,470.803,128,287.26,473.717,128,283.181,477.213,128,279.685,480.126,128,276.189,483.039,128,272.11,486.535,128,269.197,489.449,128,266.283,491.197,128,263.953,492.945,128,262.205,494.11,128,261.622,494.11,128,261.622,493.528,128,261.622,493.528,128],[400.882,442.252,128,411.953,456.819,128,413.118,457.402,128,414.283,459.15,128,416.031,461.48,128,417.78,463.228,128,420.11,465.559,128,422.441,467.89,128,424.189,470.22,128,425.354,471.386,128,426.52,473.717,128,426.52,474.882,128,426.52,475.465,128,430.016,470.22,128,431.764,467.89,128,433.512,464.976,128,436.425,460.898,128,439.339,456.819,128,442.835,452.157,128,446.331,447.496,128,448.661,443.417,128,451.575,438.756,128,453.906,435.26,128,455.654,432.929,128,456.819,430.598,128,457.402,429.433,128,455.071,433.512,128,454.488,435.26,128,453.323,437.591,128,452.157,440.504,128,450.992,442.835,128,449.827,445.748,128,448.661,448.079,128,446.913,452.157,128,444.583,456.819,128,442.252,462.646,128,439.339,469.055,128,436.425,476.047,128,432.346,483.039,128,429.433,491.197,128,425.354,498.189,128,422.441,504.016,128,420.11,508.094,128,417.78,511.008,128,416.614,512.173,128,416.031,512.756,128,415.449,512.756,128,416.031,510.425,128,416.614,508.094,128,417.78,506.346,128,418.945,503.433,128,420.693,501.102,128,423.024,497.606,128,425.937,494.11,128,428.268,490.614,128,428.268,490.614,128],[510.425,442.835,128,529.654,451.575,128,534.898,452.74,128,538.976,453.323,128,543.638,454.488,128,549.465,455.071,128,555.874,456.236,128,562.283,456.819,128,568.693,457.402,128,574.52,457.402,128,579.181,457.984,128,583.843,458.567,128,587.921,459.15,128,590.835,459.15,128,592.583,460.315,128,593.165,460.315,128,593.165,460.898,128,590.835,461.48,128,589.087,461.48,128,586.756,462.646,128,585.008,463.228,128,582.677,464.394,128,579.181,465.559,128,575.685,467.89,128,571.606,470.803,128,566.945,474.882,128,562.866,478.378,128,557.622,481.874,128,552.961,485.953,128,547.717,490.614,128,543.055,494.11,128,539.559,497.024,128,536.063,499.937,128,533.732,501.685,128,531.984,502.85,128,530.819,504.016,128,530.236,505.181,128,529.654,505.764,128,529.654,506.929,128,529.654,508.094,128,529.654,509.26,128,529.654,509.843,128,529.654,511.008,128,529.654,511.591,128,530.236,512.173,128,530.819,512.756,128,531.402,512.756,128,532.567,513.339,128,533.732,512.756,128,535.48,512.756,128,537.228,512.173,128,538.976,511.591,128,541.307,511.008,128,544.22,510.425,128,548.299,509.26,128,552.961,508.677,128,558.205,508.677,128,564.614,508.677,128,572.189,509.843,128,582.094,511.008,128,592,512.173,128,602.488,512.756,128,611.228,512.756,128,620.551,512.173,128,628.709,510.425,128,628.709,510.425,128]]")
}
