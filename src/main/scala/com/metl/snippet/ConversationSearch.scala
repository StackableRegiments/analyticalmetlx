package com.metl.snippet
{

  import com.metl.data._
  import com.metl.utils._

  import net.liftweb.util.Helpers._
  import net.liftweb.http.SHtml._
  import net.liftweb.http.js.JsCmds._
  import com.metl.view._
  import com.metl.model._
  import scala.xml._
  import net.liftweb.common._

  object RestfulConversationSearchHelper {
    val serializer = new GenericXmlSerializer(ServerConfiguration.default)
  }

  class ConversationSearch extends Logger {
    def render = "#searchInput" #> ajaxText("",query=> {
      debug("Searching for [%s]".format(query))
      if(query.length <= 1){
        SetHtml("searchResults", <div class="error">Search query must be longer than 1 character</div>)
      }
      else{
        val serverConfig = ServerConfiguration.default
        var results = serverConfig.searchForConversation(query)
        SetHtml("searchResults",results.map(c=> <a href={"/board?conversationJid=%s".format(c.jid)}>{c.title}</a>))
      }
    })
  }
}
