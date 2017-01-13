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

object JNum{
  def unapply(json:JValue) = json match{
    case JInt(x) => Some(x.toDouble)
    case JDouble(x) => Some(x)
    case _ => None
  }
}

abstract class StronglyTypedJsonActor extends CometActor with CometListener with Logger {
	protected val functionDefinitions:List[ClientSideFunction]
  case class AsyncFunctionRequest(name:String, args:List[String],serverSideFunc:List[JValue]=>JValue,returnResultFunction:Box[String],returnArguments:Boolean,params:List[JValue],bonusParams:List[JValue],start:Long){}
  object backendActor extends LiftActor {
    override def messageHandler = {
      case afr@AsyncFunctionRequest(funcName,funcArgs,func,resultFunc,returnArguments,params,bonusParams,start) => {
        val exceptionOrResult = try {
          val output = func(params)
          Right((output,params,bonusParams))
        } catch {
          case e:Exception => {
            error("exception in ClientSideFunc : %s(%s)".format(funcName,funcArgs),e)
            Left(e)
          }
        }
        val end = new java.util.Date().getTime()
        resultFunc.foreach(rrf => {
          val returnCall:JsCmd = Call("serverResponse",JObject({
            exceptionOrResult match {
              case Right((response,params,bonusParams)) => {
                JField("bonusArguments",JArray(bonusParams)) :: bonusParams.reverse.headOption.toList.map(ins => JField("instant",ins)) ::: {
                  returnArguments match {
                    case true => List(JField("arguments",JArray(params)))
                    case false => Nil
                  }
                }
              }
              case Left(e) => {
                List(JField("error",JString(e.getMessage)))
              }
            }
          } ::: List(
            JField("command",JString(funcName)),
            JField("duration",JInt(end - start)),
            JField("serverStart",JInt(start)),
            JField("serverEnd",JInt(end)),
            JField("success",JBool(exceptionOrResult.isRight))
          ))) & exceptionOrResult.right.toOption.map(res => Call(rrf,res._1):JsCmd).getOrElse(Noop)
          partialUpdate(returnCall)
        })
      }
      case _ => {}
    }
  }
  case class ClientSideFunction(val name:String,val args:List[String],val serverSideFunc:List[JValue]=>JValue,val returnResultFunction:Box[String],val returnResponse:Boolean = false,val returnArguments:Boolean = false){
    import net.liftweb.json.Extraction._
    import net.liftweb.json._
    val jsCreationFunc = Script(JsCrVar(name,AnonFunc(ajaxCall(JsRaw("JSON.stringify(augmentArguments(arguments))"),(s:String) => {
      val start = new java.util.Date().getTime()
      var async = false
      try {
        val exceptionOrResult = {
          try {
            parse(s) match {
              case jObj:JObject => {
                val allParams = jObj.children.flatMap{
                  case JField(k,v) => Some(v)
                  case _ => None
                }
                val params = allParams.take(args.length)
                val bonusParams = allParams.drop(args.length)
                if (bonusParams.exists(_ == JString("async"))){
                  async = true
                  backendActor ! AsyncFunctionRequest(name,args,serverSideFunc,returnResultFunction,returnArguments,params,bonusParams,start)
                  Right((JString("asynchronous function started"),params,bonusParams))
                } else {
                  val output = serverSideFunc(params)
                  Right((output,params,bonusParams))
                }
              }
              case unknown => Left(new Exception("unknown object: %s".format(unknown)))
            }
          } catch {
            case e:Exception => {
              Left(e)
            }
          }
        }
        val end = new java.util.Date().getTime()
        val returnCall = Call("serverResponse",JObject({
          exceptionOrResult match {
            case Right((response,params,bonusParams)) => {
              JField("bonusArguments",JArray(bonusParams)) :: bonusParams.reverse.headOption.toList.map(ins => JField("instant",ins)) ::: {
                returnArguments match {
                  case true => List(JField("arguments",JArray(params)))
                  case false => Nil
                }
              } ::: { 
                returnResponse match {
                  case true => List(JField("response",response))
                  case false => Nil
                }
              }
            }
            case Left(e) => {
              error("exception in ClientSideFunc : %s(%s)".format(name,s),e)
              List(JField("error",JString(e.getMessage)))
            }
          }
        } ::: List(
          JField("command",JString(name)),
          JField("duration",JInt(end - start)),
          JField("serverStart",JInt(start)),
          JField("serverEnd",JInt(end)),
          JField("success",JBool(exceptionOrResult.isRight))
        )))
        (for {
          rrf <- returnResultFunction
          res <- exceptionOrResult.right.toOption
          if !async
        } yield {
          returnCall & Call(rrf,res._1)
        }).getOrElse(returnCall)
      } catch {
        case e:Exception => {
          val end = new java.util.Date().getTime()
          error("Exception in ClientSideFunc::%s(%s) => %s\r\n%s".format(name,args.mkString(","),e.getMessage,ExceptionUtils.getStackTraceAsString(e)))
          Call("serverResponse",JObject(
            List(
              JField("command",JString(name)),
              JField("duration",JInt(end - start)),
              JField("serverStart",JInt(start)),
              JField("serverEnd",JInt(end)),
              JField("success",JBool(false)),
              JField("error",JString(e.getMessage))
            )))
        }
      }
      }))))
	}
  val functions = NodeSeq.fromSeq(functionDefinitions.map(_.jsCreationFunc).toList)
	override def render = NodeSeq.Empty
	override def fixedRender = {
		Stopwatch.time("StronglyTypedJsonActor.fixedRender", functions)
	}
}
