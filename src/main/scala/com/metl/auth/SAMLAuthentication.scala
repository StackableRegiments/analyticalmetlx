package monash.SAML

import com.metl.liftAuthenticator._
import net.liftweb.common._
import net.liftweb.http.{CurrentReq, _}
import net.liftweb.util.ControlHelpers.tryo
import org.pac4j.core.client.RedirectAction
import org.pac4j.core.context._
import org.pac4j.core.exception.RequiresHttpAction
import org.pac4j.saml.client.Saml2Client
import org.pac4j.saml.profile.Saml2Profile

import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.collection.mutable.{Map => scalaMutableMap}

case class OkResponseWithContent(content:String) extends LiftResponse with HeaderDefaults {
  override def toResponse = InMemoryResponse(content.getBytes, ("Content-Length", content.length.toString) :: ("Content-Type", "text/html") :: Nil, cookies, 200)
}

case class InternalServerErrorResponseWithContent(content:String) extends LiftResponse with HeaderDefaults {
  override def toResponse = InMemoryResponse(content.getBytes, ("Content-Length", content.length.toString) ::("Content-Type", "text/html") :: Nil, cookies, 500)
}

class SAMLAuthenticationSystem(samlAuthenticator:SAMLAuthenticator) extends LiftAuthenticationSystem {
  override def dispatchTableItemFilter = (request) =>
    samlAuthenticator.isRequestForSAMLCallbackUrl(request) || ( samlAuthenticator.isRequestForProtectedRoute(request) && !samlAuthenticator.checkWhetherAlreadyLoggedIn )
  override def dispatchTableItem(request:Req) =
    Full(samlAuthenticator.constructResponse(request))
}

case class SettingsForADFS(
  maximumAuthenticationLifetime:Int
)

case class keyStoreInfo(
  keystorePath:String,
  keystorePassword:String,
  privateKeyPassword:String
)

case class SAMLconfiguration(
  optionOfKeyStoreInfo:Option[keyStoreInfo] = None,
  idpMetaDataPath:String,
  serverScheme:String,
  serverName:String,
  serverPort:Int = 80,
  callBackUrl:String,
  optionOfSettingsForADFS:Option[SettingsForADFS] = None,
  optionOfEntityId:Option[String] = None,
  protectedRoutes:List[List[String]] = ("/" :: Nil) :: Nil, // Maybe think about a more complex access control system, which is represented by a List[Tuple2[List[String],Boolean]], against which the current request filters for prefixes which match it, and selects the longest of those answers to determine whether to protect or not, as represented by the boolean.
  eligibleGroups:Map[String,String] = Map.empty[String,String],
  attributeTransformers:Map[String,String] = Map.empty[String,String]
)

class SAMLAuthenticator (
  alreadyLoggedIn:() => Boolean,
  onSuccess:(LiftAuthStateData) => Unit,
  samlConfiguration: SAMLconfiguration
) extends LiftAuthenticator(alreadyLoggedIn,onSuccess) with Logger
{
  protected val overrideHost: Box[String] = Empty
  protected val overridePort: Box[Int] = Empty
  protected val overrideScheme: Box[String] = Empty

  protected def redirectHome = RedirectResponse("/")

  protected val samlClient: Saml2Client = getSaml2Client(samlConfiguration)

  protected val internalServerErrorResponseWithUnknownError = new InternalServerErrorResponseWithContent("Unknown error")

  protected val liftWebContext = new LiftWebContext(
    scheme = samlConfiguration.serverScheme,
    serverName = samlConfiguration.serverName,
    port = samlConfiguration.serverPort
  )

  protected def getSaml2Client(samlConfiguration: SAMLconfiguration) = {
    val samlClient: Saml2Client = new Saml2Client {

      // Override method "getStateParameter" to retrieve RelayState from the current request
      override def getStateParameter(webContext: WebContext): String = {
        val requestUri = CurrentReq.value.request.uri
        val boxOfQueryString = CurrentReq.value.request.queryString

        val relayState = requestUri + boxOfQueryString.map("?%s".format(_)).getOrElse("")

        relayState
      }
    }

    samlConfiguration.optionOfKeyStoreInfo match {
      case Some(keyStoreInfo: keyStoreInfo) =>
        samlClient.setKeystorePath(keyStoreInfo.keystorePath)
        samlClient.setKeystorePassword(keyStoreInfo.keystorePassword)
        samlClient.setPrivateKeyPassword(keyStoreInfo.privateKeyPassword)
      case _ =>
    }

    val serverBaseUrl =
      if (samlConfiguration.serverPort == 80) {
        "%s://%s".format(samlConfiguration.serverScheme, samlConfiguration.serverName)
      } else {
        "%s://%s:%s".format(samlConfiguration.serverScheme, samlConfiguration.serverName, samlConfiguration.serverPort)
      }

    samlClient.setIdpMetadataPath(samlConfiguration.idpMetaDataPath)
    samlClient.setCallbackUrl("%s/%s".format(serverBaseUrl, samlConfiguration.callBackUrl))

    samlConfiguration.optionOfSettingsForADFS match {
      case Some(settingsForADFS: SettingsForADFS) =>
        samlClient.setMaximumAuthenticationLifetime(settingsForADFS.maximumAuthenticationLifetime)
      case _ =>
    }

    samlConfiguration.optionOfEntityId.foreach(entityId => {
      samlClient.setIdpEntityId(entityId)
    })

    samlClient
  }

  def getSAMLClient = samlClient

  def isRequestForSAMLCallbackUrl(request: Req): Boolean = {
    val isSAMLCallbackUrl = Req.unapply(request).exists(req => {
      val partPath = req._1
      partPath == (samlConfiguration.callBackUrl :: Nil)
    })

    isSAMLCallbackUrl
  }

  def isRequestForProtectedRoute(request: Req): Boolean = {
    val isProtectedRoute = samlConfiguration.protectedRoutes.exists(protectedRoute => {
      Req.unapply(request).exists(req => {
        val partPath = req._1
        partPath == protectedRoute
      })
    })
    isProtectedRoute
  }

  override def checkWhetherAlreadyLoggedIn: Boolean = alreadyLoggedIn() || InSessionLiftAuthState.is.authenticated

  override def constructResponse(request: Req): LiftResponse = {
    request match {
      case Req(samlConfiguration.callBackUrl :: Nil, _, _) =>
        handleSAMLResponseCallback(request)
      case Req("favicon" :: Nil, _, _) =>
        ForbiddenResponse("Forbidden")
      case Req(_, _, _) =>
        sendSAMLRequest(request)
      case _ =>
        ForbiddenResponse("Forbidden")
    }
  }

  def sendSAMLRequest(request: Req): LiftResponse = {

    /***********************************
    ************************************
    *******Sample SAML Request:*********
    ************************************
    *8*********************************/

//  <?xml version="1.0" encoding="UTF-8"?>
//  <saml2p:AuthnRequest
//    xmlns:saml2p="urn:oasis:names:tc:SAML:2.0:protocol"
//    AssertionConsumerServiceURL="http://localhost:8081/saml-callback"
//    Destination="https://login-qa.monash.edu/adfs/ls/"
//    ForceAuthn="false"
//    ID="_48899f2c16f845e9354fdf282bbb928a"
//    IsPassive="false"
//    IssueInstant="2015-03-25T06:08:50.379Z"
//    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
//    ProviderName="pac4j-saml"
//    Version="2.0"
//  >
//    <saml2:Issuer xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion">
//      Monash Default SAML Service Provider
//    </saml2:Issuer>
//  </saml2p:AuthnRequest>

    val boxOfResult = tryo { samlClient.getRedirectAction(liftWebContext, true, false) }

    boxOfResult match {
      case Full(redirectAction:RedirectAction) => {
        redirectAction.getType match {
          case RedirectAction.RedirectType.REDIRECT =>
            RedirectResponse(redirectAction.getLocation)
          case RedirectAction.RedirectType.SUCCESS =>
            OkResponseWithContent(redirectAction.getContent)
          case other =>
            val errorMessage = "Unsupported SAML redirection type: %s".format(other.toString)
            error(errorMessage)
            new InternalServerErrorResponseWithContent(errorMessage)
        }
      }
      case Failure(message:String, boxOfThrowable:Box[Throwable], _) => {
        boxOfThrowable.map {
          case exception: RequiresHttpAction =>
            error("Error code",exception)
            exception.getCode match {
              case 401 => new UnauthorizedResponse("Unauthenticated request")
              case 403 => new ForbiddenResponse("Forbidden")
              case _ => internalServerErrorResponseWithUnknownError
            }
          case other =>
            error("Unknown exception caught",other)
            internalServerErrorResponseWithUnknownError
        }.openOr(internalServerErrorResponseWithUnknownError)
      }
      case other =>
        error("Unsupported result found: %s".format(other.toString))
        internalServerErrorResponseWithUnknownError
    }
  }

  def handleSAMLResponseCallback(request: Req): LiftResponse = {

    val boxOfRedirectResponse = tryo {
      val credentials = samlClient.getCredentials(liftWebContext)
      val userProfile: Saml2Profile = samlClient.getUserProfile(credentials, liftWebContext)

      val rawAttributes = userProfile.getAttributes
      debug("raw saml attrs: %s".format(rawAttributes))
      val attributes:List[Tuple2[String,String]] = rawAttributes.asScala.toList.flatMap {
        case ( name: String, arrayList: java.util.ArrayList[String] ) => {
          debug("decoding attribute: %s as an arrayList[String]: %s".format(name,arrayList))
          arrayList.toArray.toList.map(arr => ( name, arr.toString ) )
        }
        case ( name: String, arrayList: java.util.ArrayList[Object] ) => {
          debug("decoding attribute: %s as an arrayList[Object]: %s".format(name,arrayList))
          arrayList.toArray.toList.map(arr => ( name, arr.toString ) )
        }
        case ( name: String, str: String ) => {
          debug("decoding attribute: %s as a string: %s".format(name,str))
          List(( name, str ))
        }
      }.toList

      debug("all attrs for '%s': %s\r\nroles: %s".format(userProfile.getId,attributes,userProfile.getRoles))

      // This is where we might want to adjust the LiftAuthStateData to
      // support the attributes and groups returned by the SAML packet.
      val groups = attributes.flatMap(attr => {
        samlConfiguration.eligibleGroups.get(attr._1).map(groupType => (groupType,attr._2))
      }).toList
      val transformedAttrs = attributes.flatMap(attr => {
        samlConfiguration.attributeTransformers.get(attr._1).map(attrName => (attrName,attr._2))
      }).toList
      val liftAuthStateData = LiftAuthStateData(
        authenticated = true,
        username = userProfile.getId,
        eligibleGroups = groups,//List.empty[(String, String)],
        informationGroups =  attributes ::: transformedAttrs
      )

      // Let's think about rewriting LiftAuthenticator to remove the SessionVar from it, making that
      // the responsibility of the consuming app, now that we have apps which don't want to remember your session username.
      debug("setting inSessionState: %s".format(liftAuthStateData))
      InSessionLiftAuthState.set(liftAuthStateData)

      debug("firing onSuccess")
      onSuccess(liftAuthStateData)

      // Let's use the RelayState returned from  the IDP
      val redirectResponse = CurrentReq.value.param("RelayState").map(relayState => {
        new RedirectResponse(relayState, request)
      }).getOrElse(redirectHome)
      debug("redirecting to: %s".format(redirectResponse))
      redirectResponse
    }

    boxOfRedirectResponse match {
      case Full(redirectResponse:RedirectResponse) => redirectResponse
      case Failure(message: String, boxOfThrowable: Box[Throwable], _) => {
        boxOfThrowable.foreach(bot => error("Exception thrown when retrieving user credentials from SAML response",bot))
        internalServerErrorResponseWithUnknownError
      }
      case Failure(_, boxOfThrowable: Box[Throwable], _) => {
        boxOfThrowable.foreach(bot => error("Exception thrown when retrieving user credentials from SAML response",bot))
        internalServerErrorResponseWithUnknownError
      }
      case other => {
        error("Unknown exception thrown when retrieving user credentials from SAML response: %s".format(other.toString))
        internalServerErrorResponseWithUnknownError
      }
    }
  }
}
