package com.metl.model

import com.metl.external.{ExternalGradebook, ExternalGradebookConfigurator}
import net.liftweb.common.Logger

import scala.xml._

trait ReflectionUtil extends Logger {
  protected def getExternalClasses[VendedClass,VendorClass <: {def configureFromXml(in:NodeSeq):Either[Exception,List[VendedClass]]}](className:String,properties:NodeSeq):Either[Exception,List[VendedClass]] = {
    try {
      val clazz = getClass.getClassLoader.loadClass(className)
      val instance = clazz.newInstance
      val vendor = instance.asInstanceOf[VendorClass]
      val vended = vendor.configureFromXml(properties)
      warn("created class: %s, which created: %s".format(vendor,vended))
      vended
    } catch {
      case e:Exception => {
        error("exception thrown while using reflection to instantiate class: %s => %s".format(className,e.getMessage))
        Left(e)
      }
    }
  }
}

object ExternalGradebooks extends ReflectionUtil {
  def configureFromXml(in:NodeSeq):List[ExternalGradebook] = {
    (in \\ "externalLibGradebookConfigurator").toList.flatMap(n => {
      for {
        className <- (n \ "@className").headOption.map(_.text).toList
        result:ExternalGradebook <- getExternalClasses[ExternalGradebook,ExternalGradebookConfigurator](className,n).right.toOption.getOrElse(Nil)
      } yield {
        result
      }
    })
  }
}