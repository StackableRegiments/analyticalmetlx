package com.metl.model

import com.metl.external.{GroupsProvider, GroupsProviderConfigurator}

import scala.xml.NodeSeq

object ExternalGroupsProviders extends ReflectionUtil {
  /*
  def configureFromXml(in:NodeSeq):List[GroupsProvider] = {
      for {
        className <- (in \ "@className").headOption.map(_.text).toList
        result:GroupsProvider <- getExternalClasses[GroupsProvider,GroupsProviderConfigurator](className,in).right.toOption.getOrElse(Nil)
      } yield {
        result
      }

  }
  */
}
