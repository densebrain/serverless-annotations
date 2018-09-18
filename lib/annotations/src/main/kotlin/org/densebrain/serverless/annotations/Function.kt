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
  val environment:Array<Environment> = [],
  val timeout: Int = 30,
  val reservedConcurrency: Int = 0,
  val memorySize: Int = 1024,
  val http: Array<HttpEvent> = [],
  val awsProxy: Array<HttpEvent> = [],
  val lambdaProxy: Array<HttpEvent> = [],
  val schedule: Array<ScheduleEvent> = [],
  val cloudwatch: Array<CloudwatchEvent> = [],
  val custom: Array<CustomEvent> = []
)