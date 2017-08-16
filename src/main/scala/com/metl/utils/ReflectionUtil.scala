package com.metl.utils

import net.liftweb.common.Logger

import scala.xml.NodeSeq

trait ReflectionUtil extends Logger {
  protected def getExternalClasses[VendedClass,VendorClass <: {def configureFromXml(in:NodeSeq):Either[Exception,List[VendedClass]]}](className:String,properties:NodeSeq):Either[Exception,List[VendedClass]] = {
    constructExternalClasses[VendedClass,VendorClass](className,(vendor:VendorClass) => vendor.configureFromXml(properties).right.get)
  }
  protected def constructExternalClasses[VendedClass,VendorClass](className:String,action:VendorClass=>List[VendedClass]):Either[Exception,List[VendedClass]] = {
    try {
      val clazz = getClass.getClassLoader.loadClass(className)
      val instance = clazz.newInstance
      val vendor = instance.asInstanceOf[VendorClass]
      val vended = action(vendor)
      warn("created class: %s, which created: %s".format(vendor,vended))
      Right(vended)
    } catch {
      case e:Exception => {
        error("exception thrown while using reflection to instantiate class: %s => %s".format(className,e.getMessage))
        Left(e)
      }
    }
  }
}
