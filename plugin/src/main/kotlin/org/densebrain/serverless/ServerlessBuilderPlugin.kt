package org.densebrain.serverless

import org.gradle.api.Plugin
import org.gradle.api.Project

open class ServerlessBuilderPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    //project.tasks.create("serverlessBuild", ServerlessBuilderTask::class.java)
  }
}