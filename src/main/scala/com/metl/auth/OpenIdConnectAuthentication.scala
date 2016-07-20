package com.metl.auth

import net.liftweb._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._
import net.liftweb.http._
import com.metl.liftAuthenticator._
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JsCmds._
import scala.xml._

class OpenIdConnectAuthenticationSystem(googleClientId:String,googleAppDomainName:Option[String],alreadyLoggedIn:()=>Boolean,onSuccess:LiftAuthStateData => Unit) extends LiftAuthenticator(alreadyLoggedIn,onSuccess) with LiftAuthenticationSystem with Logger{
  import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken,GoogleIdTokenVerifier}
  import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload

  val transport = new com.google.api.client.http.javanet.NetHttpTransport()
  val jsonFactory = new com.google.api.client.json.jackson2.JacksonFactory()
  val verifier:GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
      .setAudience(scala.collection.JavaConversions.asJavaCollection(List(googleClientId)))
      .build()

  def constructResponse(r:Req,originalRequestId:String):LiftResponse = {
    val nodes = 
      <html lang="en">
        <head>
          <meta name="google-signin-scope" content="profile email"></meta>
          <meta name="google-signin-client_id" content={googleClientId}></meta>
          <script src="https://apis.google.com/js/platform.js" async="true" defer="true"></script>
        </head>
        <body>
          <div class="g-signin2" data-onsuccess="onSignIn" data-theme="dark"></div>
          <script>{Unparsed("""
            function onSignIn(googleUser) {
              // Useful data for your client-side scripts:
              /*
              var profile = googleUser.getBasicProfile();
              console.log("ID: " + profile.getId()); // Don't send this directly to your server!
              console.log("Name: " + profile.getName());
              console.log("Image URL: " + profile.getImageUrl());
              console.log("Email: " + profile.getEmail());
              */
             /*
              var rawPathPossibility = new RegExp( '[?&]' + field + '=([^&#]*)', 'i' ).exec(window.location.href);
              var pathValue = rawPathPossibility ? rawPathPossibility[1] : "/";
              */
              var pathValue = "%s";
              var originalRequestId = "%s";
              var id_token = googleUser.getAuthResponse().id_token;
              console.log("ID Token: " + id_token);
              var form = document.createElement("form");
              form.setAttribute("method","post");
              form.setAttribute("action","verifyOpenIdConnectToken");
              var pathField = document.createElement("input");
              pathField.setAttribute("type","hidden");
              pathField.setAttribute("name","path");
              pathField.setAttribute("value",pathValue);
              form.appendChild(pathField);
              var tokenField = document.createElement("input");
              tokenField.setAttribute("type","hidden");
              tokenField.setAttribute("name","googleIdToken");
              tokenField.setAttribute("value",id_token);
              form.appendChild(tokenField);
              var originalRequestField = document.createElement("input");
              originalRequestField.setAttribute("type","hidden");
              originalRequestField.setAttribute("name","originalRequestId");
              originalRequestField.setAttribute("value",originalRequestId);
              form.appendChild(originalRequestField);
              document.body.appendChild(form);
              form.submit();
            };
            """.format(r.param("path").openOr(r.uri),originalRequestId))
          }</script>
        </body>
      </html>
    new InMemoryResponse(nodes.toString.getBytes("UTF-8"),Nil,Nil,200)
  }

  override def dispatchTableItemFilter = (r) => false
  protected def dispatchTableItemFilterInternal:Req=>Boolean = (r) => !checkWhetherAlreadyLoggedIn
  override def dispatchTableItem(r:Req,originalReqId:String) = Full(constructResponse(r,originalReqId))
  LiftRules.dispatch.prepend {
    case r@Req("verifyOpenIdConnectToken" :: Nil,_,_) if dispatchTableItemFilterInternal(r) && r.post_? => () => {
      (for (
          path <- S.param("path");      
          idTokenString <- S.param("googleIdToken");
          originalRequestId <- S.param("originalRequestId")
      ) yield {
        verifier.verify(idTokenString) match {
          case null => {
            dispatchTableItem(r,originalRequestId)
          }
          case idToken:GoogleIdToken  => {
            val payload:Payload = idToken.getPayload
            if (googleAppDomainName.map(gadn => payload.getHostedDomain() == gadn).getOrElse(true)){
              val userId:String = payload.getSubject()
              debug("authenticated: %s".format(
                Map(
                  "email" -> payload.getEmail,
                  "userId" -> userId
                  )
                )) 
              //fetch further information with:
              //"https://www.googleapis.com/plus/v1/people/%s".format(userId)
              //alternatively, we can just use the email address as the username, and appropriately safety it for use in the app, if the app doesn't like '@'
              val lasd = LiftAuthStateData(true,userId,Nil,Some(payload.getEmail).filterNot(_ == null).map(e => ("email",e)).toList)
              //InSessionLiftAuthState(lasd) //not storing it inside the authenticator, so that the app can choose the persistence strategy.
              onSuccess(lasd)
              Full(RedirectResponse(path))
            } else {
              dispatchTableItem(r,originalRequestId)
            }
          }
        }
      }).getOrElse(S.param("originalRequestId").flatMap(originalRequestId => dispatchTableItem(r,originalRequestId)))
    }
  }
}
