package com.metl.comet

import com.metl.comet.ScanDirections.Below
import com.metl.data._
import net.liftweb.common.{Full, Logger}
import net.liftweb.http.js.JE.Call
import net.liftweb.json.JObject
import net.liftweb.util.Schedule
import org.fluttercode.datafactory.impl.DataFactory

import scala.xml.{Elem, Text}

case class TrainingManual(actor:TrainerActor) extends Logger {
  import Dimensions._
  val namer = new DataFactory
  def el(label:String,content:String) = Elem.apply(null,label,scala.xml.Null,scala.xml.TopScope,Text(content))
  def p(content:String) = TrainingInstruction(el("p",content))
  def makeStudent(intent:Intention) = TrainingControl(
    "Bring in some students".format(intent),c => {
      c.progress
      actor.users = SimulatedUser("%s %s".format(namer.getFirstName, namer.getLastName),ClaimedArea(0,0,0,lineHeight,0),Point(0,0,0),Below,Intentions.Benign,Watching(0),List.empty[SimulatedActivity]) :: actor.users
    }).reps(3)
  val createInkTracker:TrainingControl = TrainingControl(
    "Draw a few strokes",
    _ => {
      actor ! new StanzaTrigger(s => {
        s match {
          case i: MeTLInk if actor isHuman i =>
            createInkTracker.progress
            actor ! RefreshControls
          case _ =>
        }
        s
      })
      actor ! ShowClick("#drawMode")
    }
  ).reps(3)
  val createTextTracker:TrainingControl = TrainingControl(
    "Add a couple of text boxes",
    _ => {
      actor ! new StanzaTrigger(s => {
        s match {
          case i: MeTLMultiWordText if actor isHuman i =>
            createTextTracker.progress
            actor ! RefreshControls
          case _ =>
        }
        s
      })
      actor ! ShowClick("#insertText")
    }
  ).reps(2)
  val createImageTracker:TrainingControl = TrainingControl(
    "Insert an image",
    _ => {
      actor ! new StanzaTrigger(s => {
        s match {
          case i: MeTLImage if actor isHuman i =>
            createImageTracker.progress
            actor ! RefreshControls
          case _ =>
        }
        s
      })
      actor ! ShowClick("#insertMode")
    }
  ).reps(1)

  private def countSelectedByType(selection: JObject, valueType: String):Int = {
    val count = selection.values.get(valueType).map {
      case m: Map[String, Any] => m.size
      case _ => 0
    }.getOrElse(0)
/*    (for {
      typed@JObject <- selection \ valueType
      (itemId, item) <- typed.values
    } yield {
      itemId
    }).length*/
//    debug("Selected " + valueType + ": " + count)
    count
  }

  val selectInkTracker:TrainingControl = TrainingControl(
    "Select an ink stroke",
    _ => {
      actor ! new AuditTrigger((action, params) => {
//        actor.logAudit(action, params, "Audit ink select")
        action match {
          case a:String if a.equals("selectionChanged") && countSelectedByType(params, "inks") > 0 =>
            selectInkTracker.progress
            actor ! RefreshControls
          case _ =>
        }
        None
      })
      actor ! ShowClick("#selectMode")
    }
  ).reps(2)

  class TrainingReturnTo(pageNumber:Int)
    extends TrainingNavigator("Take me back to exercise " + pageNumber, actor, pageNumber)

  val pages:List[TrainingPage] = List(
    TrainingPage(Text("Exercise 1"),
      Text("The teaching space"),
      List(
        p("This space is a whiteboard on which you and your class can write."),
        p("Like a slide deck or a PowerPoint presentation, it supports multiple pages."),
        TrainingControl(
          "Show me the pages",
          _ => actor ! Flash("#thumbsColumn")
        ),
        p("You may choose whether your class can move freely between these."),
        TrainingControl("How do I move between pages?",
          _ => actor ! Flash("#slideControls")
        ),
        p("Only the author of the conversation can add pages."),
        TrainingControl(
          "Show me how",
          _ => {
            Schedule.schedule(actor,Flash("#addSlideButton"),1000)
            actor ! Highlight("#slideControls")
          }
        ),
        p("Classroom spaces, the device you're using and network speed can affect whether the whiteboard works well for you."),
        TrainingControl(
          "How can I tell if there's a problem?",
          _ => {
            actor ! Flash(".meters")
          }).supplementary(List(
          "These three meters indicate three kinds of health.",
          "The topmost meter watches for a healthy network connection, checking every time you do any action and also checking in the background periodically.  If the network ever fails health check completely, a red boundary will appear around these meters and remain red for the next five minutes.  This is to make you aware of a poor environment.",
          "The center meter is about whether all of your students are with you.  This measures the number of students who are allowed to attend this session against the number of students who have joined the conversation.  This is only effective when your conversation is restricted to a particular enrolment context.  In this particular case, you are in a private conversation; only you are allowed to join, and only you are here.  One hundred percent!",
          "The third meter measures overall activity in the room.  The green curve above the line indicates public activity, and the red curve below the line measures private activity.  This is the only interaction you are permitted to have with private content."
        )),
        p("In the next exercise, we'll do some work on the pages."),
        TrainingNavigator("Show me how to create content", actor, 2)),
      Full(
        Call("Trainer.clearTools").cmd
      ),
      List(new StanzaTrigger((stanza:MeTLStanza) => { actor.logStanza(stanza,"Exercise 1")}))
    ),
    TrainingPage(Text("Exercise 2"),
      Text("Sharing an open space"),
      List(
        p("In this trainer you can bring virtual students into your classroom.  These students will stay with you during your training session."),
        makeStudent(Intentions.Benign),
        p("The students you create can work anywhere, even outside of where you are currently looking."),
        p("To observe all their work, set your camera to include all content no matter where it appears."),
        TrainingControl(
          "Show me how to watch everything",
          _ => {
            Schedule.schedule(actor,ShowClick("#zoomToFull"),1000)
            actor ! ShowClick("#zoomMode")
          }
        ),
        p("If you just want to concentrate on your own work, set your camera not to move automatically."),
        TrainingControl(
          "Show me how to stop the camera moving",
          _ => {
            Schedule.schedule(actor,ShowClick("#zoomToCurrent"),1000)
            actor ! ShowClick("#zoomMode")
          }
        ),
        p("Now let's make some of your own content."),
        TrainingNavigator("Show me the rest of the tools", actor, 3),
        new TrainingReturnTo(1)
      ),
      Full(
        Call("Trainer.clearTools").cmd &
          Call("Trainer.highlight","#toolsColumn").cmd &
          Call("Trainer.hide",".permission-states").cmd &
          Call("Trainer.hide","#floatingToggleContainer").cmd &
          Call("Trainer.hide",".meters").cmd
      ),
      List(new StanzaTrigger((stanza:MeTLStanza) => { actor.logStanza(stanza,"Exercise 2")}))
    ),
    TrainingPage(Text("Exercise 3"),
      Text("Your creative space"),
      List(
        p("You have control over whether your content appears to other users (Public) or just yourself (Private)."),
        TrainingControl(
          "Which controls do that?",
          _ => {
            actor ! Highlight("#toolsColumn")
            actor ! Flash(".permission-states")
          }
        ).supplementary(List(
          "Your selection of Public versus Private at the time you add new content will determine whether that content is visible to others.",
          "It is always visible to you, and you can change your mind later."
        )),
        p("You can add several kinds of content to the space."),
        p("Try drawing some lines.  You can use your finger, or a stylus, or a mouse."),
        createInkTracker,
        p("Try writing some text."),
        createTextTracker,
        p("Try inserting an image.  You can use any image stored on your device."),
        createImageTracker,
        p("Once you have added content, you may need to move it, resize it, hide or show it."),
        TrainingNavigator("Show me how to modify existing content", actor, 4),
        new TrainingReturnTo(2)
      ),
      Full(Call("Trainer.showTools").cmd),
      List(new StanzaTrigger((stanza:MeTLStanza) => { actor.logStanza(stanza,"Exercise 3")}))
    ),
    TrainingPage(Text("Exercise 4"),
      Text("Modifying your creations"),
      List(
        p("You can select content by clicking on it or dragging across it. " +
          "Clicking selects the top element, and dragging selects all elements that are touched by the selection box."),
        p("Try selecting a line you drew earlier."),
        selectInkTracker,
        new TrainingReturnTo(3)
      ),
      Full(Call("Trainer.showTools").cmd),
      List(new StanzaTrigger((stanza:MeTLStanza) => { actor.logStanza(stanza,"Exercise 4")}))
    )
  )
}
