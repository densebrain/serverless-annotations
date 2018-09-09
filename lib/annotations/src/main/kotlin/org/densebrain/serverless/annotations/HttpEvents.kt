package org.densebrain.serverless.annotations

import kotlin.reflect.KClass



annotation class Parameter(
  val name:String,
  val required:Boolean = false,
  val description:String = ""
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
    "Content-Type",
    "Authorization",
    "X-Amz-Date",
    "X-Api-Key",
    "X-Amz-Security-Token",
    "X-Amz-User-Agent"
  )
)

enum class HttpEventMethod {
  GET,
  POST,
  PUT,
  DELETE,
  PATCH,
  OPTIONS,
  HEAD
}

annotation class Documentation(
  val description:String = "",
  val summary:String = ""
)

annotation class HttpEvent(
  val path: String,
  val method: HttpEventMethod = HttpEventMethod.GET,
  val input: KClass<*> = KClass::class,
  val output: KClass<*> = KClass::class,
  val cors: CORSConfig = CORSConfig(),
  val request:Request = Request(),
  val documentation:Documentation = Documentation()
)

