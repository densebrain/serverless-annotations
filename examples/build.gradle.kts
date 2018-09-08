import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.densebrain.serverless.*

buildscript {
  dependencies {
    classpath("org.densebrain:serverless-plugin")
    classpath("com.github.jengelman.gradle.plugins:shadow:2.0.4")
  }
}

plugins {
  base
  java
  kotlin("jvm") version "1.2.61"
}

val annotationBuild = gradle.includedBuild("serverless-builder").task(":serverless-annotations:build")
val pluginBuild = gradle.includedBuild("serverless-builder").task(":serverless-plugin:build")

subprojects {
  apply<ShadowPlugin>()
  apply<ServerlessBuilderPlugin>()
  apply(plugin = "java")
  apply(plugin = "kotlin")

  dependencies {
    "implementation"("org.densebrain:serverless-annotations")
    "implementation"("com.amazonaws:aws-lambda-java-core:1.1.0")
    "implementation"("com.amazonaws:aws-lambda-java-events:2.0.1")
  }

  val shadowJar = tasks.getByName<ShadowJar>("shadowJar") {
    archiveName = "${project.name}.jar"
    classifier = ""
  }

  tasks.getByName("build").dependsOn(annotationBuild)
  shadowJar.dependsOn(annotationBuild)

  val serverlessBuildTask = tasks.create<ServerlessBuilderTask>("serverlessBuild") {
    dependsOn(pluginBuild,shadowJar)

    archive = file("${buildDir.absolutePath}/libs/${shadowJar.archiveName}")
    outputFile = file("${projectDir.absolutePath}/serverless.yml")
    basePackage = "org.densebrain.serverless.examples"
  }

  tasks.getByName("build").finalizedBy(
    serverlessBuildTask
  )
}