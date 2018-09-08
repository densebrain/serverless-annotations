package org.densebrain.serverless

import org.densebrain.serverless.annotations.Function
import org.gradle.api.Project
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicBoolean

class ServerlessFunctionBuilder(private val project:Project) {

  private val log
    get() = project.logger

  private val processed = AtomicBoolean(false)
  private val functionConfig = mutableMapOf<String,Any>()

  fun getFunctionConfig(archive: File, basePackage:String):Map<String,Any> {
    if (!processed.getAndSet(true)) {
      val archiveUrl = archive.toURI().toURL()
      log.quiet("Archive URL: ${archiveUrl}")
      val uclUrls = arrayOf(archiveUrl)
      val ucl = URLClassLoader(uclUrls, Thread.currentThread().contextClassLoader)
      Thread.currentThread().contextClassLoader = ucl

      val config = ConfigurationBuilder()
        .addClassLoaders(ucl)
        .setExpandSuperTypes(false)
        .setUrls(ClasspathHelper.forPackage(basePackage, ucl))
        .setScanners(SubTypesScanner(false), TypeAnnotationsScanner())
        .filterInputsBy(FilterBuilder().includePackage(basePackage))

      val reflections = Reflections(config)
      val funcs = reflections.getTypesAnnotatedWith(Function::class.java)
      funcs.forEach {
        val clazz = it.kotlin
        val func = clazz.annotations.find { it is Function }!! as Function
        log.quiet("Processing function: ${clazz.simpleName} / ${func.name}")
      }
    }

    return functionConfig
  }
}