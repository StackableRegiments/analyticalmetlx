package com.metl.model

import com.metl.external.{ExternalGradebook, ExternalGradebookConfigurator}
import com.metl.utils.ReflectionUtil

import scala.xml._

object ExternalGradebooks extends ReflectionUtil {
  def configureFromXml(in:NodeSeq):List[ExternalGradebook] = {
    (in \\ "externalLibGradebookProvider").toList.flatMap(n => {
      for {
        className <- (n \ "@className").headOption.map(_.text).toList
        result:ExternalGradebook <- getExternalClasses[ExternalGradebook,ExternalGradebookConfigurator](className,n).right.toOption.getOrElse(Nil)
      } yield {
        result
      }
    })
  }
}