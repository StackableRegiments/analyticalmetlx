package com.metl.utils

object FunctionConverter {
  implicit def scalaFunctionToJava[From, To](function: (From) => To): java.util.function.Function[From, To] = {
    new java.util.function.Function[From, To] {
      override def apply(input: From): To = function(input)
    }
  }
}