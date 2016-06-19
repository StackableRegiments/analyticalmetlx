package com.metl.model
import java.net.URLEncoder.encode
import net.liftweb.common._
import scala.xml._
import com.metl.model._
import com.metl.data._
import com.metl.view._
import com.metl.utils._
import net.liftweb.json._
import JsonDSL._

case class Notebook(name:String,id:String,sections:Seq[NotebookSection])
case class NotebookSection(name:String,id:String,pagesUrl:String)

object OneNote {
  val config = ServerConfiguration.default
  implicit val formats = DefaultFormats

  val notebookTitle = "MeTL"
  val notebooks = "https://www.onenote.com/api/v1.0/me/notes/notebooks?expand=sections"
  val sections = "https://www.onenote.com/api/v1.0/me/notes/notebooks/%s/sections"
  val pages = "https://www.onenote.com/api/v1.0/me/notes/sections/%s/pages"
  val codeClaim = "https://login.live.com/oauth20_authorize.srf?client_id=%s&scope=%s&response_type=%s&redirect_uri=%s"
  val tokenClaim = "https://login.live.com/oauth20_token.srf"

  val filePath = Globals.configurationFileLocation
  val propFile = XML.load(filePath)
  val root = propFile \\ "oneNote"
  val redirectUrl = encode((root \ "redirectUrl").text, "utf-8")
  val scopes = encode((root \ "scopes").text, "utf-8")
  val clientId = encode((root \ "clientId").text,"utf-8")
  val clientSecret = encode((root \ "clientSecret").text,"utf-8")
  val responseType = "code"
  val authUrl = codeClaim.format(clientId,scopes,responseType,redirectUrl)

  val client = Http.getClient
  val bucketSize = 30

  def bearer(token:String) = List(("Authorization","Bearer %s".format(token)))

  def inMeTLNotebook(token:String,func:Notebook =>Box[String]):Box[String] = {
    loadNotebooks(token).filter(_.name == notebookTitle).headOption match {
      case Some(metlNotebook) => {
        func(metlNotebook)
      }
      case None => {
        client.postBytes(
          notebooks,
          compact(render(("name" -> notebookTitle))).getBytes,
          ("Content-type","application/json; charset=utf-8") :: bearer(token))
        inMeTLNotebook(token,func)
      }
    }
  }
  def inSection(conversation:Conversation,token:String,notebook:Notebook,func:NotebookSection => Box[String]):Box[String] = {
    val title = conversation.title.filterNot(escape.contains _).take(50).mkString
    notebook.sections.filter(_.name == title).headOption match {
      case Some(conversationSection) => {
        "Conversation exists: %s".format(conversationSection)
        func(conversationSection)
      }
      case None => {
        val body = compact(render((("name" -> title))))
        client.postBytes(
          sections.format(notebook.id),
          body.getBytes,
          ("Content-type","application/json; charset=utf-8") :: bearer(token))
        export(conversation.jid.toString,token)
      }
    }
  }
  def blockName(slide:Slide) = "IMAGE%s".format(slide.id)
  def html(content:Seq[Node]) = (<html>{content}</html>).toString
  def image(slide:Slide,width:String="1024",height:String="768") = {
    val src = "name:%s".format(blockName(slide))
    <div>
    <img src={src} width={width} height={height} />
    </div>
  }
  val escape = """?*\/:<>|&#"%~()-""".toSet
  def export(conversationJid:String,token:String):Box[String] = {
    val bucketSize = 30
    inMeTLNotebook(token, notebook => {
      val details = config.detailsOfConversation(conversationJid)
      inSection(details,token,notebook, section => {
        details.slides.sortBy(_.index).grouped(bucketSize).map(slides =>
          new String(client.postMultipart(
            pages.format(section.id),
            slides.map(s => (blockName(s), HttpResponder.getSnapshotWithPrivate(s.id.toString,"medium"))),
            List(("Presentation",html(slides.map(s => image(s))))),
            bearer(token)
          ))
        ).toList.headOption
      })
    })
  }
  def load(token:String)(in:Any) = {
    in match {
      case u:String => parse(client.get(u,bearer(token)))
      case JString(text) => parse(client.get(text,bearer(token)))
    }
  }
  def claimToken(code:String) = {
    val claim = client.postUnencodedForm(tokenClaim,List(
      ("client_id",clientId),
      ("redirect_uri",redirectUrl),
      ("client_secret",clientSecret),
      ("code",code),
      ("grant_type","authorization_code")
    ))
    val JString(token) = parse(new String(claim,"utf-8")) \ "access_token"
    token
  }
  def loadNotebooks(token:String) = {
    val json = load(token)(notebooks)
    println(json)
      (json \ "value").extract[List[Notebook]]
  }
}
