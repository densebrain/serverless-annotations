package org.densebrain.serverless.annotations

import kotlin.reflect.KClass


enum class FunctionEventType(val value: String, val clazz: KClass<*>) {
  Http("http", HttpEvent::class),
  LambdaProxy("lambda-proxy", HttpEvent::class),
  AwsProxy("aws-proxy", HttpEvent::class),
  Schedule("schedule", HttpEvent::class)
}

annotation class Environment(
  val name:String,
  val value:String = "",
  val yaml: String = ""
)

annotation class Function(
  val name: String,
  val environment:Array<Environment> = arrayOf(),
  val timeout: Int = 30,
  val http: Array<HttpEvent> = arrayOf(),
  val awsProxy: Array<HttpEvent> = arrayOf(),
  val lambdaProxy: Array<HttpEvent> = arrayOf(),
  val schedule: Array<ScheduleEvent> = arrayOf(),
  val cloudwatch: Array<CloudwatchEvent> = arrayOf()
)