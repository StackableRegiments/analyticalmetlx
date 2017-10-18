package com.metl.modules

import net.liftweb._
import http._
import json._
import common._
import util._
import Helpers._
import scala.collection.mutable.{HashMap=>MutMap}
import com.metl.model.Globals

// instances of modules will be hotloadable in the system.  instances of modules should start up and shutdown on command.

class Module(val ModuleName:String, val ModuleCategory:String) extends Logger {
  protected var creator:Option[ModulesController] = None
  def init(mc:ModulesController):Unit = {
    creator = Some(mc)
    warn("started module: %s %s".format(ModuleName,ModuleCategory))
  }
  def shutdown:Unit = {
    creator = None
    warn("stopped module: %s %s".format(ModuleName,ModuleCategory))
  }
  def asJFields:List[JField] = List(JField("name",JString(ModuleName)),JField("category",JString(ModuleCategory)))
}

class DispatchingModule(override val ModuleName:String, override val ModuleCategory:String) extends Module(ModuleName,ModuleCategory){
  def dispatch:PartialFunction[Req,Box[LiftResponse]] = {
    case r@Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: _,_,_) => innerDispatch(r)
  }
  protected def innerDispatch:PartialFunction[Req,Box[LiftResponse]] = {
    case Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: "version" :: Nil,_,_) => Full(PlainTextResponse("1.0",200))
  }
}
class StatelessDispatchingModule(override val ModuleName:String, override val ModuleCategory:String) extends Module(ModuleName,ModuleCategory){
  def statelessDispatch:PartialFunction[Req,Box[LiftResponse]] = {
    case r@Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: _,_,_) => innerStatelessDispatch(r)
  }
  protected def innerStatelessDispatch:PartialFunction[Req,Box[LiftResponse]] = {
    case Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: "version" :: Nil,_,_) => Full(PlainTextResponse("1.0",200))
  }
}

class MockPlainTextDispatcher(override val ModuleName:String, override val ModuleCategory:String) extends DispatchingModule(ModuleName,ModuleCategory){
  override protected def innerDispatch = {
    case Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: "echo" :: message :: Nil, _, _) => Full(JsonResponse(JObject(asJFields ::: List(JField("message",JString(message))))))
  }
}
class MockDispatcherCreator(override val ModuleName:String, override val ModuleCategory:String) extends DispatchingModule(ModuleName,ModuleCategory){
  override protected def innerDispatch = {
    case Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: "create" :: category :: name :: Nil, _, _) => {
      creator.map(mc => {
        mc.addModule(category,new MockPlainTextDispatcher(name,category))
        JsonResponse(JObject(asJFields ::: List(JField("created",JObject(List(JField("name",JString(name)),JField("category",JString(category))))))))
      })
    }
    case Req(ModulesController.ModulesPath :: ModuleCategory :: ModuleName :: "remove" :: category :: name :: Nil, _, _) => {
      creator.map(mc => {
        mc.removeModule(category,name)
        JsonResponse(JObject(asJFields ::: List(JField("removed",JObject(List(JField("name",JString(name)),JField("category",JString(category))))))))
      })
    }
  } 
}

/*
// of course we can't modify the sitemap after boot, but perhaps we can do something clever with deferred functions. 
class SiteMapModule(val ModuleName:String, val ModuleCategory:String) extends Module(ModuleName,ModuleCategory){
}
*/

abstract class GroupsModule(override val ModuleName:String) extends Module(ModuleName,ModuleCategories.GROUP) {
  //def getGroupsFor(account:Option[Account],profileId:Option[String]):List[Group]
}

// I think perhaps auth modules might be either special, or there'll be an extra dispatch behaviour to handle auth specially.

abstract class AuthModule(override val ModuleName:String/*,onSuccess:AuthStateData=>Unit*/) extends Module(ModuleName,ModuleCategories.AUTH) {
  def getDisplayName:String
  def getImageUrl:String
  override def asJFields:List[JField] = super.asJFields ::: List(JField("displayName",JString(getDisplayName)),JField("imageUrl",JString(getImageUrl)))
}

/*
abstract class AccountSpecificModule(override val ModuleName:String,override val ModuleCategory:String,accountPredicate:Account=>Boolean) extends Module(ModuleName,ModuleCategory){
  // the idea here is that it should be possible to have some modules only available to particular accounts or account providers, so that we can be a little more granular about what's available throughout the shared system.
}
*/

object ModuleCategories {
  val GROUP = "groupProvider"
  val AUTH = "authProvider"

}

// this bit would be called by Boot

object ModulesController {
  val ModulesPath:String = "modules"
}

class ModulesController extends Logger {
  protected val modules:MutMap[String,List[Module]] = new MutMap[String,List[Module]]()
  def addModule(category:String,m:Module):Unit = {
    m.init(this)
    modules.update(category,m :: modules.get(category).getOrElse(Nil))
  }
  def removeModule(category:String,name:String):Unit = {
    val (removed,retained) = modules.get(category).getOrElse(Nil).partition(_.ModuleName == name)
    modules.update(category,retained)
    removed.foreach(_.shutdown)
  }
  def listModules(category:String):List[Module] = modules.get(category).getOrElse(Nil)
  def listCategories:List[String] = modules.keys.toList
  def init:Unit = {
    warn("starting modules subsystem")
    LiftRules.dispatch.append{
      case Req(ModulesController.ModulesPath :: "listCategories" :: Nil,_,_) if true || Globals.isSuperUser => () => Full(JsonResponse(JArray(listCategories.map(c => JString(c))),200)) 
      case Req(ModulesController.ModulesPath :: moduleCategory :: Nil,_,_) if true || Globals.isSuperUser => () => Full(JsonResponse(JArray(listModules(moduleCategory).map(m => JObject(m.asJFields))),200))
      case r@Req(ModulesController.ModulesPath :: moduleCategory :: moduleName :: _,_,_) => () => {
        for {
          c <- modules.get(moduleCategory)
          m <- c.find(_.ModuleName == moduleName)
          resp <- m match {
            case d:DispatchingModule => d.dispatch(r)
            case _ => Empty
          }
        } yield {
          resp
        }
      }
    }
    LiftRules.statelessDispatch.append{
      case r@Req(ModulesController.ModulesPath :: moduleCategory :: moduleName :: rest,_,_) if listModules(moduleCategory).find(_.ModuleName == moduleName).exists(_.isInstanceOf[StatelessDispatchingModule]) => () => {
        for {
          c <- modules.get(moduleCategory)
          m <- c.find(_.ModuleName == moduleName)
          resp <- m match {
            case d:StatelessDispatchingModule => d.statelessDispatch(r)
            case _ => Empty
          }
        } yield {
          resp
        }
      }
    }
    addModule("mock",new MockDispatcherCreator("creator","mock"))
  }
  def shutdown:Unit = {
    warn("shutting down modules subsystem")
  }
}
