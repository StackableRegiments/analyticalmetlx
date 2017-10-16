package bootstrap.liftweb

import _root_.net.liftweb.util._
import Helpers._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.rest._
import _root_.net.liftweb.http.provider._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import com.metl.snippet._
import com.metl.view._
import com.metl.model._
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds._

object Boot{
  val ninetyDays = 60*60*24*90
  val cacheStrongly = List(
    ("Cache-Control","public, max-age=%s".format(ninetyDays)),
    ("Pragma","public")
  )
  val noCache = List(
    ("Cache-Control","private, no-cache, max-age=0"),
    ("Pragma","private")
  )
}

class Boot extends Logger {
   def boot {
    trace("Boot begins")
    LiftRules.addToPackages("com.metl")

    trace("Config begins")
    // this starts up our system - populates serverConfigurations, attaches CAS, attaches RestHelpers, etc.
    MeTLXConfiguration.initializeSystem
    trace("Routing begins")
    val defaultHeaders = LiftRules.defaultHeaders
    val isDebug = Props.mode match {
      case Props.RunModes.Production => false
      case Props.RunModes.Staging => false
      case Props.RunModes.Pilot => false
      case _ => true
    }
    LiftRules.defaultHeaders = {
      case (_, Req("static"::"js"::"stable"::_, _, _)) => Boot.noCache
      case (_, Req("proxyDataUri"::_, _, _)) => Boot.cacheStrongly
      case (_, Req("proxy"::_, _, _)) => Boot.cacheStrongly
      case (_, Req("static" ::_,_,_)) => Boot.noCache
      case any => defaultHeaders(any)
    }
    LiftRules.supplementalHeaders.default.set(List(
      ("Access-Control-Allow-Origin", "*"),
      ("Access-Control-Allow-Credentials", "true"),
      ("Access-Control-Allow-Methods", "GET, OPTIONS"),
      ("Access-Control-Allow-Headers", "WWW-Authenticate,Keep-Alive,User-Agent,X-Requested-With,Cache-Control,Content-Type")
    ))
    LiftRules.attachResourceId = {
      if (isDebug){
        s => "%s?%s".format(s,nextFuncName)
      } else {
        val prodRunId = nextFuncName
        s => "%s?%s".format(s,prodRunId)
      }
    }
    LiftRules.passNotFoundToChain = false
    LiftRules.uriNotFound.prepend {
      case (Req("static":: rest,_,_),failure) => {
        debug("staticResource uriNotFound: %s".format(rest))
        DefaultNotFound
      }
      case _ => NotFoundAsResponse(RedirectResponse(com.metl.snippet.Metl.conversationSearch()))
    }

    LiftRules.noCometSessionCmd.default.set(() => Reload:JsCmd)

    def sitemap() = SiteMap(
      Menu(Loc("about","about" :: Nil,"About MeTL")), // licenses and whatnot.
      Menu(Loc("releaseNotes","releaseNotes" :: Nil,"Release Notes")),
      //API catalog
      Menu(Loc("API","catalog" :: Nil,"Application Programming Interfaces")),
      //2011 piggyback auth
      Menu(Loc("Authentication",Link("authenticationState" :: Nil,true,"/authenticationState"),"Authentication",Hidden)),
      Menu.i("menu.saml") / "saml-callback" >> Hidden,
      //MeTLX
    //  Menu(Loc("Home","index" :: Nil,"Home")),
      Menu(Loc("MeTL Viewer","metlviewer" :: Nil,"MeTL Viewer")),
      Menu(Loc("Board","board" :: Nil,"MeTL X")),
      Menu(Loc("EditConversation","editConversation" :: Nil,"Edit Conversation")),
      Menu(Loc("PrintConversation","printConversation" :: Nil,"Print Conversation")),
      Menu(Loc("ClientSidePrintConversation","clientSidePrintConversation" :: Nil,"Client Side Print Conversation")),
      Menu(Loc("SearchConversations","searchConversations" :: Nil,"Search Conversations")),
      Menu(Loc("Summaries","summaries" :: Nil,"Analytics")),
      Menu(Loc("Dashboard","dashboard" :: Nil,"Conversation level dashboarding")),
      Menu(Loc("Enterprise dashboard","enterprise" :: Nil,"Enterprise level dashboarding")),
      Menu(Loc("Enterprise statistics","statistics" :: Nil,"Enterprise level statistics")),
      Menu(Loc("Course activity","activity" :: Nil,"Course activity")),
      //WebMeTL
      Menu(Loc("Conversation Search","conversationSearch" :: Nil,"Conversation Search",Hidden)),
      Menu(Loc("Conversation","conversation" :: Nil,"Conversation",Hidden)),
      Menu(Loc("Import Powerpoint","importPowerpoint" :: Nil,"Import Powerpoint",Hidden)),
      Menu(Loc("WebMeTL Conversation search","conversations" :: Nil,"Conversations",Hidden)),
      Menu(Loc("Slide","slide" :: Nil,"Slide",Hidden)),
      Menu(Loc("SlidePrev","slidePrev" :: Nil,"Previous Page",Hidden)),
      Menu(Loc("SlideNext","slideNext" :: Nil,"Next Page",Hidden)),
      Menu(Loc("SlideNavigation","slideNavigation" :: Nil,"Slide Navigation",Hidden)),
      Menu(Loc("SlideTitle","slideTitle" :: Nil,"Slide Title",Hidden)),
      Menu(Loc("Quiz","quiz" :: Nil,"Quiz",Hidden)),
      Menu(Loc("Quizzes","quizzes" :: Nil,"Quizzes",Hidden)),
      Menu(Loc("RemotePluginConversationChooser","remotePluginConversationChooser" :: Nil,"LTI Remote Plugin",Hidden)),
      //Default
      Menu(Loc("Static", Link(List("static"), true, "/static/index"), "Static Content", Hidden)),
      //Help
      Menu(Loc("reportProblem", "reportProblem" :: Nil, "Report a Problem")),
      //test endpoint
      Menu(Loc("testCreateComet", "testCreateComet" :: Nil, "Test Comet Creation"))
    )

    LiftRules.setSiteMapFunc(() => sitemap())

    LiftRules.loggedInTest = Full(() => true)
    info("Started version: %s\r\n".format(com.metl.BuildInfo.version)) // initialize the loading of the version number in the app, for the about page, and also dump it into the logs so that we can see it.
//    info("release-notes:\r\n%s".format(com.metl.snippet.VersionFacts.releaseNotes.mkString("\r\n")))
    net.liftweb.http.SessionMaster.sessionWatchers = List(com.metl.comet.SessionMonitor)
    trace("Boot ends")
  }
}
