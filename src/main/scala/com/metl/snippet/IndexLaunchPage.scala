package com.metl.snippet

import com.metl.data._
import com.metl.utils._

import com.metl.model._
import scala.xml._
import net.liftweb.http.SHtml._
import net.liftweb.http.S
import net.liftweb.util.Helpers._
import java.util.Date

object IndexLaunchPage {
  val metl2011InstallerLink:Option[String] = (XML.load(Globals.configurationFileLocation) \\ "metl2011InstallUrl").headOption.map(_.text).filter(t => t match {
    case null => false
    case "" => false
    case _ => true
  })
  def render = {
    metl2011InstallerLink.map(url => {
      "#metl2011Installer *" #> {
        "#metl2011InstallerLink [href]" #> url
      }
    }).getOrElse({
      "#metl2011Installer" #> NodeSeq.Empty
    })
  }
}
