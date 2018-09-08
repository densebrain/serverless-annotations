import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.densebrain.serverless.*

buildscript {
  dependencies {
    classpath(files(Artifacts.pluginJar))
  }
}

plugins {
  id("com.github.johnrengelman.shadow")
}

subprojects {
  apply<ShadowPlugin>()
//  if (Artifacts.)
  //apply<ServerlessBuilderPlugin>()
  apply(plugin = "java")
  apply(plugin = "kotlin")

  dependencies {
    "implementation"(project(":annotations"))
    "implementation"("com.amazonaws:aws-lambda-java-core:1.1.0")
    "implementation"("com.amazonaws:aws-lambda-java-events:2.0.1")
  }

  val shadowJar = tasks.getByName<ShadowJar>("shadowJar") {
    archiveName = "${project.name}.jar"
    classifier = ""
  }

  val serverlessBuildTask = tasks.create<ServerlessBuilderTask>("serverlessBuild") {
    dependsOn(shadowJar)

    archive = file("${buildDir.absolutePath}/libs/${shadowJar.archiveName}")
    outputFile = file("${projectDir.absolutePath}/serverless.yml")
    basePackage = "org.densebrain.serverless.examples"
  }

  tasks.getByName("build").finalizedBy(
    serverlessBuildTask
  )
}