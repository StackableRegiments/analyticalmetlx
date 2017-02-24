package com.metl.snippet

import com.metl.data.ServerConfiguration
import net.liftweb.common.Logger
import net.liftweb._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.SHtml._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCommands._
import net.liftweb.json.JsonAST.JString
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.mapper._

object Query extends Query

class Query extends Logger {

  def render = {
    "#queryButton" #> ajaxButton("Bang", () => {
      // Client side
      val result = JString("Bang on the client")
      val bob:JsCmd = jsExpToJsCmd(Call("makeANoise", result))
      bob
    }) &
      "#queryParam" #> ajaxText("stuff", (param) => {
        // Server side
        println("User entered: " + param)
      }) &
      "#script" #> Script(JsCrVar("fred", AnonFunc(ajaxCall(JsRaw("JSON.stringify(arguments)"), (s:String) => {
        val jObj = net.liftweb.json.parse(s)
        Call("makeANoise", jObj)
      }))))
  }

//  DB.runQuery("SELECT * FROM ", )
  // returns (List[String] headers, List[List[String]] data)
}
