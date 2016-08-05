package com.metl.liftExtensions

import com.metl.utils._

import net.liftweb._
import common._
import http._
import util._
import Helpers._
import HttpHelpers._
import actor._
import scala.xml._
import SHtml._

import js._
import JsCmds._
import JE._

import json.JsonAST._

object ClientUpdate{
  def unapply(json:JValue) = json match{
    case JObject(List(JField("command",JString(command)), JField("params",args))) => Some(command,args)
    case _ => None
  }
}
object JNum{
  def unapply(json:JValue) = json match{
    case JInt(x) => Some(x.toDouble)
    case JDouble(x) => Some(x)
    case _ => None
  }
}
class ClientSideFunctionDefinition(val name:String,val args:List[String],val serverSideFunc:List[Any]=>JValue,val returnResultFunction:Box[String]){
  override def equals(a:Any) = a match {
    case ClientSideFunctionDefinition(aName,aArgs,aServerSideFunc,aReturnResultFunction) => aName == name && aArgs == args && aServerSideFunc == serverSideFunc && aReturnResultFunction == returnResultFunction
    case _ => false
  }
}
object ClientSideFunctionDefinition {
  def apply(name:String,args:List[String],serverSideFunc:List[Any]=>JValue,returnResultFunction:Box[String]) = new ClientSideFunctionDefinition(name,args,serverSideFunc,returnResultFunction)
  def unapply(in:ClientSideFunctionDefinition):Option[Tuple4[String,List[String],List[Any]=>JValue,Box[String]]] = {
    Some((in.name,in.args,in.serverSideFunc,in.returnResultFunction))
  }
}
abstract class StronglyTypedJsonActor extends CometActor with CometListener {
	protected val functionDefinitions:List[ClientSideFunctionDefinition]
	private def createResponse(name:String,requestId:Option[String],success:Boolean,result:Option[JValue] = None,duration:Option[Long] = None):JsCmd = {
    Call("serverResponse",JObject(List(JField("command",JString(name)),JField("success",JBool(success))) ::: requestId.map(r => List(JField("commandId",JString(r)))).getOrElse(List.empty[JField]) ::: result.map(r => JField("response",r)).toList ::: duration.map(d => JField("duration",JInt(d))).toList))
	}
  object ClientSideFunction {
    def apply(name:String,args:List[String],serverSideFunc:List[Any]=>JValue,returnResultFunction:Box[String]) = {
      new ClientSideFunction(name,args,serverSideFunc,returnResultFunction)
    }
    def unapply(in:ClientSideFunction):Option[Tuple4[String,List[String],List[Any]=>JValue,Box[String]]] = {
      Some((in.name,in.args,in.serverSideFunc,in.returnResultFunction))
    }
  }
	class ClientSideFunction(val name:String,val args:List[String],val serverSideFunc:List[Any]=>JValue,val returnResultFunction:Box[String]){
    override def equals(a:Any) = a match {
      case ClientSideFunction(aName,aArgs,aServerSideFunc,aReturnResultFunction) => aName == name && aArgs == args && aServerSideFunc == serverSideFunc && aReturnResultFunction == returnResultFunction
      case _ => false
    }
      
		private def deconstruct(input:JValue):Any = {
			input match {
				case j:JObject => j
				case JString(s) => s
				case JInt(i) => i
				case JNum(n) => n
				case JArray(l) => l.map(deconstruct(_))
				case j:JValue => j
				case other => other.toString
			}
		}
		private def deconstructArgs(funcArgs:JValue):Tuple2[Option[String],List[Any]] = {
			funcArgs match {
				case JArray(l) if (l.length > 1) => (l.headOption.map(s => deconstruct(s).toString),l.tail.map(deconstruct(_)))
				case JArray(l) => (l.headOption.map(s => deconstruct(s).toString),List.empty[Any])
				case JString(s) => (Some(s),List.empty[Any])
				case JNum(d) => (Some(d.toString),List.empty[String])
				case JNull => (None,List.empty[String])
				case obj:JValue => (None,List(obj))
				case _ => (None,List.empty[String])
			}
		}
		private def matchesRequirements(funcArgs:JValue):Boolean = {
			args.length match {
				case 0 => funcArgs match {
					case JArray(u) => u.length == 1
					case u:JValue => true
					case _ => false
				}
				case other => funcArgs match {
					case JArray(u) => u.length == other + 1
					case _ => false
				}
			}
		}
		private val jsonifiedArgs:JsExp = {
			val requestIdentifier = "new Date().getTime().toString()"
			val constructList = (l:List[String]) => JsRaw("[%s]".format(l.mkString(",")))
			args match {
				case Nil => JsRaw(requestIdentifier)
				case List(arg) => constructList(List(requestIdentifier,arg))
				case l:List[String] if (l.length > 1) => constructList(requestIdentifier :: l)
				case _ => JNull
			}
		}
		val jsCreationFunc = Script(Function(name,args,jsonSend(name,jsonifiedArgs)))
		def matchingFunc(input:Any):Tuple3[Boolean,Option[String],Option[JValue]] = input match {
			case funcArgs:JValue if matchesRequirements(funcArgs) => {
				val (reqId,deconstructedArgs) = deconstructArgs(funcArgs)
				try {
					val output = Stopwatch.time("MeTLActor.ClientSideFunction.%s.serverSideFunc".format(name),{
						serverSideFunc(deconstructedArgs)
					})
					returnResultFunction.map(rrf => {
						partialUpdate(Call(rrf,output))
					})
					(true,reqId,None)
				} catch {
					case e:Throwable => 
						(false,reqId,Some(JString(e.getMessage)))
					case other => 
						(false,reqId,Some(JString(other.toString)))
				}
			}
			case other:JValue => (false,Some(input.toString),Some(JObject(List(JField("error",JString("request didn't match function requirements")),JField("providedParams",other)))))
			case other => (false,Some(input.toString),Some(JObject(List(JField("error",JString("request didn't match function requirements")),JField("unknownParams",JString(other.toString))))))
		}
	}
	case object ClientSideFunctionNotFound extends ClientSideFunction("no function",List.empty[String],(l) => JNull,Empty)
	val strongFuncs = Map(functionDefinitions.map(fd => (fd.name,ClientSideFunction(fd.name,fd.args,fd.serverSideFunc,fd.returnResultFunction))):_*)
  val functions = NodeSeq.fromSeq(strongFuncs.values.map(_.jsCreationFunc).toList)
	override def render = NodeSeq.Empty
	override def fixedRender = {
		Stopwatch.time("StronglyTypedJsonActor.fixedRender", functions)
	}
	override def receiveJson = {
		case ClientUpdate(commandName,commandParams) => {
			val c = strongFuncs.getOrElse(commandName,ClientSideFunctionNotFound)
      val duration = new java.util.Date().getTime
			val (success,requestIdentifier,optionalMessage) = c.matchingFunc(commandParams)
			createResponse(c.name,requestIdentifier,success,optionalMessage,Some(new java.util.Date().getTime - duration))
		}
    case other => createResponse("unknown",None,false,Some(JString(other.toString)))
  }
}
