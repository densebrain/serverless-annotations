package org.densebrain.serverless.annotations

annotation class ScheduleEvent(
  val schedule: String,
  val enabled:Boolean = true,
  val name:String = "",
  val description:String = ""
)
