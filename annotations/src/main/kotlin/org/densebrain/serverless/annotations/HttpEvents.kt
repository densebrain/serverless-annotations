package org.densebrain.serverless.annotations

import kotlin.reflect.KClass


annotation class Parameter(
  val name:String,
  val required:Boolean = false
)

annotation class Request(
  val paths:Array<Parameter> = arrayOf(),
  val querystrings:Array<Parameter> = arrayOf(),
  val headers:Array<Parameter> = arrayOf()
)

annotation class CORSConfig(
  val enabled: Boolean = true,
  val origin:String = "*",
  val allowCredentials:Boolean = false,
  val maxAge:Int = 86400,
  val headers: Array<String> = arrayOf(
    "page-size",
    "page-number",
    "total-items",
    "Content-Type",
    "Authorization",
    "X-Amz-Date",
    "X-Api-Key",
    "X-Amz-Security-Token",
    "X-Amz-User-Agent"
  )
)

enum class HttpMethod {
  GET,
  POST,
  PUT,
  DELETE,
  PATCH,
  OPTIONS,
  HEAD
}

annotation class HttpEvent(
  val path: String,
  val method: HttpMethod = HttpMethod.GET,
  val input: KClass<*> = KClass::class,
  val output: KClass<*> = KClass::class,
  val cors: CORSConfig = CORSConfig(),
  val request:Request = Request()
)

