package com.metl.data

import scala._

import com.metl.data._
import Privacy._
import scala.xml._

trait QueryXml extends XmlUtils {

    @throws(classOf[IllegalArgumentException])
    def queryXml[T](label: String)(implicit mf: ClassManifest[T], content: NodeSeq):T = {

        val dispatch = Map(
          classOf[Privacy] -> ((node: NodeSeq, elem: String) => getPrivacyByName(node, elem)),
          classOf[Color] -> ((node: NodeSeq, elem: String) => getColorByName(node, elem)),
          classOf[String] -> ((node: NodeSeq, elem: String) => getStringByName(node, elem)),
          classOf[Boolean] -> ((node: NodeSeq, elem: String) => getBooleanByName(node, elem)),
          classOf[Double] -> ((node: NodeSeq, elem: String) => getDoubleByName(node, elem)),
          classOf[Long] -> ((node: NodeSeq, elem: String) => getLongByName(node, elem)),
          classOf[Int] -> ((node: NodeSeq, elem: String) => getIntByName(node, elem))
        )

        (() => dispatch.find(_._1 isAssignableFrom mf.erasure).map(_._2))() match {
            case Some(x) => x(content, label).asInstanceOf[T]
            case None => throw new IllegalArgumentException("Type not supported") 
        }
    }
}
