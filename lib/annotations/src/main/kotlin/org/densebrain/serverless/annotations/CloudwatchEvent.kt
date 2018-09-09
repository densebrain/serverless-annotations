package org.densebrain.serverless.annotations

annotation class CloudwatchEvent(
  // THIS SHOULD BE YAML
  val event:String
)