package com.metl.utils

import org.htmlcleaner._

import scala.collection.JavaConversions._
import scala.collection._

object HtmlParser{
	def getAttributeValueAndContentFromPageElements(pageBody:String,searchAttributeKey:String,searchAttributeValue:String,targetAttributeKey:String):List[(String,String)] = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementListByAttValue(searchAttributeKey,searchAttributeValue,true,true).toList.map(item => {
        val node = item.asInstanceOf[TagNode]
        (node.getText.toString,node.getAttributeByName(targetAttributeKey))
      }).toList
  }
	def getAttributeValueFromPageElements(pageBody:String,searchAttributeKey:String,searchAttributeValue:String,targetAttributeKey:String):List[String] = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementListByAttValue(searchAttributeKey,searchAttributeValue,true,true).toList.map(node => node.asInstanceOf[TagNode].getAttributeByName(targetAttributeKey)).toList
  }
	def getAttributesForElementsByAttValue(pageBody:String,searchAttributeKey:String,searchAttributeValue:String):List[Map[String,String]] = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementListByAttValue(searchAttributeKey,searchAttributeValue,true,true).toList.map(node => {
			Map(node.asInstanceOf[TagNode].getAttributes.entrySet.toList.map(mapEntry => (mapEntry.getKey.toString, mapEntry.getValue.toString)):_*)
		}).toList
	}
  def getAttributeValueFromPageElement(pageBody:String,searchAttributeKey:String,searchAttributeValue:String,targetAttributeKey:String):String = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementListByAttValue(searchAttributeKey,searchAttributeValue,true,true).toList match {
      case Nil => "unknown"
      case headNode :: _ => headNode.asInstanceOf[TagNode].getAttributeByName(targetAttributeKey)
    }
  }
  def getAttributeValuesFromPageElement(pageBody:String,searchAttributeKey:String,targetAttributeKeys:List[String]):List[List[(String,String)]] = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementsHavingAttribute(searchAttributeKey,true).toArray.toList.map(node => targetAttributeKeys.map(attr => (attr,node.getAttributeByName(attr))).toList).toList
  }
  def getAttributeValuesAndContentsFromPageElement(pageBody:String,searchAttributeKey:String,targetAttributeKeys:List[String]):List[(List[(String,String)],String)] = {
    val xml = new HtmlCleaner().clean(pageBody)
    xml.getElementsHavingAttribute(searchAttributeKey,true).toList.map(node =>{
      (targetAttributeKeys.map(attr => (attr,node.getAttributeByName(attr))).toList,node.getText.toString)
    }).toList
  }
	def getContentOfElementsByAttValue(pageBody:String,searchAttributeKey:String,searchAttributeValue:String) = {
		val xml = new HtmlCleaner().clean(pageBody)
		xml.getElementsByAttValue(searchAttributeKey,searchAttributeValue,true,true).toArray.toList.map(s=>s.asInstanceOf[TagNode].getChildren.toString)
	}
}
