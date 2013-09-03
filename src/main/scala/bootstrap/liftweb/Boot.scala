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

object Boot{
  val ninetyDays = 60*60*24*90
  val cacheStrongly = List(
    ("Cache-Control","public, max-age=%s".format(ninetyDays)),
    ("Pragma","public")
  )
}

class Boot {
  def boot {
    println("Boot begins")
    LiftRules.addToPackages("com.metl")

    LiftRules.allowParallelSnippets.session.set(true)
    LiftRules.maxConcurrentRequests.session.set((r:Req)=>1000)

    LiftRules.cometRequestTimeout = Full(25)

    println("Config begins")
    // this starts up our system - populates serverConfigurations, attaches CAS, attaches RestHelpers, etc.
    MeTLXConfiguration.initializeSystem
    println("Routing begins")
    val defaultHeaders = LiftRules.defaultHeaders
    LiftRules.defaultHeaders = {
      case (_, Req("static"::"js"::"stable"::_, _, _)) => Boot.cacheStrongly
      case (_, Req("proxyDataUri"::_, _, _)) => Boot.cacheStrongly
      case (_, Req("proxy"::_, _, _)) => Boot.cacheStrongly
      case any => defaultHeaders(any)
    }

    LiftRules.passNotFoundToChain = false
    LiftRules.uriNotFound.prepend {
      case (Req("static":: rest,_,_),failure) => {
        println("staticResource uriNotFound: %s".format(rest))
        DefaultNotFound
      }
      case _ => NotFoundAsResponse(RedirectResponse("/board"))
    }

    def sitemap() = SiteMap(
      //MeTLX
      Menu(Loc("Board","board" :: Nil,"Main face to face",Hidden)),
      Menu(Loc("Summaries","summaries" :: Nil,"Analytics",Hidden)),
      //WebMeTL
      Menu("Directory") / "directory",
      Menu("Home") / "index",
      Menu("Conversation") / "conversation",
      Menu("Slide") / "slide",
      Menu("SlidePrev") / "slidePrev",
      Menu("SlideNext") / "slideNext",
      Menu("SlideNavigation") / "slideNavigation",
      Menu("SlideTitle") / "slideTitle",
      Menu("Quiz") / "quiz",
      Menu("Quizzes") / "quizzes",
      //Default
      Menu(Loc("Static", Link(List("static"), true, "/static/index"), "Static Content", Hidden)))

    LiftRules.setSiteMapFunc(() => sitemap())

    LiftRules.loggedInTest = Full(() => true)
    println("Boot ends")
  }
}
