package com.metl.model

import com.metl.external.{ExternalMeTLingPotAdaptorConfigurator, MeTLingPotAdaptor}
import com.metl.utils.ReflectionUtil
import scala.xml.NodeSeq

object ExternalMeTLingPotAdaptors extends ReflectionUtil {
  def configureFromXml(in:NodeSeq):Either[Exception,List[MeTLingPotAdaptor]] = {
    Right((in \\ "externalMetlingPotProvider").toList.flatMap(n => {
      for {
        className <- (n \ "@className").headOption.map(_.text).toList
        result:MeTLingPotAdaptor <- constructExternalClasses[MeTLingPotAdaptor,ExternalMeTLingPotAdaptorConfigurator](className,configurator => {
          configurator.configureFromXml(n).right.toOption.getOrElse(Nil)
        }).right.toOption.getOrElse(Nil).headOption
      } yield {
        MeTLingPot.wrapWith(n,result)
      }
    }))
  }
}
