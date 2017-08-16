package com.metl.model

import com.metl.external.{ExternalLtiConfigurator, LtiIntegration}
import com.metl.utils.ReflectionUtil

import scala.xml.NodeSeq

object ExternalLtiIntegrations extends ReflectionUtil {
  def configureFromXml(in:NodeSeq):Either[Exception,List[LtiIntegration]] = {
    Right((in \\ "externalLibLtiProvider").toList.flatMap(n => {
      for {
        className <- (n \ "@className").headOption.map(_.text).toList
        result:LtiIntegration <- constructExternalClasses[LtiIntegration,ExternalLtiConfigurator](className,configurator => {
          configurator.configureFromXml(n,com.metl.snippet.Metl,() => Globals.currentUser.is,() => Globals.isDevMode).right.toOption.getOrElse(Nil)
        }).right.toOption.getOrElse(Nil).headOption
      } yield {
        result
      }
    }))
  }
}
