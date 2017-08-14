package com.metl.model

import com.metl.external.{GroupStoreProviderConfigurator, GroupStoreProvider}

import scala.xml.NodeSeq

object ExternalGroupStoreProviders extends ReflectionUtil {
  /*
  def configureFromXml(in:NodeSeq):List[GroupStoreProvider] = {
      for {
        className <- (in \ "@className").headOption.map(_.text).toList
        result:GroupStoreProvider <- getExternalClasses[GroupStoreProvider,GroupStoreProviderConfigurator](className,in).right.toOption.getOrElse(Nil)
      } yield {
        result
      }

  }
  */
}
