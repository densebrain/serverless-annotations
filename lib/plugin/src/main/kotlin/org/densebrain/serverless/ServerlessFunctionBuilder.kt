package org.densebrain.serverless

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.densebrain.serverless.annotations.*
import org.densebrain.serverless.annotations.Function
import org.gradle.api.Project
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

class ServerlessFunctionBuilder(
  protected val project: Project,
  protected val schemaOutputDir: File = File("${project.buildDir.absolutePath}/schema")
) {

  private val log
    get() = project.logger

  private val processed = AtomicBoolean(false)
  private val functionConfig = mutableMapOf<String, Map<String, Any>>()
  private val clazzSchemaMap = mutableMapOf<KClass<*>, String>()

  protected val schemaGenerator = JsonSchemaGenerator(mapper, useExternalReferencing = true)

  var generateDocumentation = false


  fun getDocumentationModels():Map<String,Any> {
    return mapOf(
      "api" to mapOf(
        "info" to mapOf(
          "version" to 1,
          "description" to "API"
        )
      ),
      "models" to clazzSchemaMap.map { (key,schemaPath) ->
        val name = schemaNameFromPath(schemaPath)

        return@map mapOf(
          "name" to name,
          "description" to name,
          "contentType" to "application/json",
          "schema" to mapper.readValue(File(schemaPath), mapTypeRef)
        )
      }
    )
  }

  /**
   * Build all function configurations
   */
  fun getFunctionConfigs(
    archive: File,
    basePackage: String
  ): Map<String, Any> {
    if (processed.getAndSet(true))
      return functionConfig

    val archiveUrl = archive.toURI().toURL()
    log.quiet("Archive URL: ${archiveUrl}")
    val uclUrls = arrayOf(archiveUrl)
    val ucl = URLClassLoader(uclUrls, Thread.currentThread().contextClassLoader)
    Thread.currentThread().contextClassLoader = ucl

    val reflections = Reflections(ConfigurationBuilder()
      .addClassLoaders(ucl)
      .setExpandSuperTypes(false)
      .setUrls(ClasspathHelper.forPackage(basePackage, ucl))
      .setScanners(SubTypesScanner(false), TypeAnnotationsScanner())
      .filterInputsBy(FilterBuilder().includePackage(basePackage)))

    val funcClazzType = Class.forName(Function::class.java.name,false,ucl) as Class<Annotation>
    val funcClazzes = reflections.getTypesAnnotatedWith(funcClazzType)

    // DO SCHEMAS FIRST
    if (generateDocumentation) {
      val schemaClazzType = Class.forName(JsonSchema::class.java.name,false,ucl) as Class<Annotation>
      val schemaClazzes = reflections.getTypesAnnotatedWith(schemaClazzType)
      schemaClazzes.forEach { clazz -> storeSchema(clazz.kotlin) }
    }

    // FUNCS
    funcClazzes.forEach { funcClazz ->
      log.quiet("Processing: ${funcClazz.name}")

      val clazz = funcClazz.kotlin
      val func = clazz.annotations.find { anno -> anno is Function }!! as Function
      functionConfig[func.name] = with(func) {
        val config = FunctionConfig(clazz.qualifiedName!!)
        http.forEach { httpEvent -> config.addEvent(func,httpEvent) }
        schedule.forEach { scheduleEvent -> config.addEvent(func,scheduleEvent) }
        cloudwatch.forEach { cloudwatchEvent -> config.addEvent(func,cloudwatchEvent) }
        environment.forEach(config::addEnvironment)
        config
      }.toMap()


      log.quiet("Processing function: ${clazz.simpleName} / ${func.name}")
    }


    return functionConfig
  }

  /**
   * Schema name from path
   */
  protected fun schemaNameFromPath(schemaPath:String):String = schemaPath
    .split("/")
    .last()
    .replace(".json","")
    .split(".")
    .last()

  /**
   * Store a class schema
   */
  protected fun storeSchema(clazz: KClass<*>): String {
    if (!generateDocumentation)
      return ""

    if (clazzSchemaMap[clazz] != null)
      return clazzSchemaMap[clazz]!!

    schemaOutputDir.mkdirs()
    val schema = schemaGenerator.generateJsonSchema(clazz.java)
    val schemaFile = File(schemaOutputDir, "${clazz.qualifiedName}.json")
    schemaFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema))

    log.quiet("Wrote schema for ${clazz.qualifiedName} to ${schemaFile.absolutePath}")
    clazzSchemaMap[clazz] = schemaFile.absolutePath
    return schemaFile.absolutePath
  }

  /**
   * Represents a function
   */
  @Suppress("unused")
  inner class FunctionConfig(
    val handler: String,
    val environment: MutableMap<String, Any> = mutableMapOf(),
    val timeout: Int = 30,
    val events: MutableList<Map<String, Any>> = mutableListOf()
  ) {

    /**
     * Convert an array of func params to a required map
     */
    private fun paramsToMap(params: Array<Parameter>): Map<String, Boolean> {
      return params.foldRight(mutableMapOf(), { param, map ->
        map[param.name] = param.required
        map
      })
    }

    private fun paramsToList(params: Array<Parameter>): List<Map<String,Any>> {
      return params.map {param -> mapOf(
        "name" to param.name,
        "description" to param.description,
        "required" to param.required
      )}
    }

    /**
     * Add an environment variable to the config
     */
    fun addEnvironment(env: Environment) {
      val value:Any = when {
        env.yaml.isNotBlank() -> yaml.load(env.yaml) as Map<String,Any>
        else -> env.value
      }
      environment[env.name] = value
    }


    /**
     * Add cloud watch event to events
     */
    fun addEvent(func:Function, event: CloudwatchEvent) {
      events.add(mapOf(
        "cloudwatchEvent" to yaml.load(event.event)
      ))
    }

    /**
     * Add schedule event to events
     */
    fun addEvent(func:Function, event: ScheduleEvent) {
      events.add(mapOf(
        "schedule" to event.schedule,
        "enabled" to event.enabled,
        "name" to if (event.name.isBlank()) func.name else event.name,
        "description" to if (event.description.isBlank()) func.name else event.description
      ))
    }

    /**
     * Add http event to the config
     */
    fun addEvent(func:Function, event: HttpEvent) {
      val httpEvent = mutableMapOf(
        "path" to event.path,
        "method" to event.method.name.toLowerCase(),
        "cors" to when {
          !event.cors.enabled -> "false"
          else -> mapOf(
            "origin" to event.cors.origin,
            "maxAge" to event.cors.maxAge,
            "headers" to event.cors.headers,
            "allowCredentials" to event.cors.allowCredentials
          )
        },
        "request" to mapOf(
          "parameters" to mapOf(
            "paths" to paramsToMap(event.request.paths),
            "querystrings" to paramsToMap(event.request.querystrings),
            "headers" to paramsToMap(event.request.headers)
          ).filter { (key, values) -> values.isNotEmpty() }
        )
      )

      // IF DOCUMENTATION IS ENABLED
      if (generateDocumentation) {
        val inputSchemaPath =
          if (event.input != KClass::class) storeSchema(event.input) else ""

        val outputSchemaPath =
          if (event.output != KClass::class) storeSchema(event.output) else ""

        val documentation = mutableMapOf(
          "summary" to func.name,
          "description" to func.name,
          "requestHeaders" to paramsToList(event.request.headers),
          "pathParams" to paramsToList(event.request.paths)
        )

        if (inputSchemaPath.isNotBlank()) {
          documentation["requestModels"] = mapOf(
            "application/json" to schemaNameFromPath(inputSchemaPath)
          )
        }

        if (outputSchemaPath.isNotBlank()) {
          val modelName = schemaNameFromPath(outputSchemaPath)
          documentation["methodResponses"] = arrayOf(
            mapOf(
              "statusCode" to "200",
              "responseBody" to mapOf(
                "description" to modelName
              ),
              "responseModels" to mapOf(
                "application/json" to modelName
              )
           )
          )
        }

        httpEvent["documentation"] = documentation
      }

      events.add(mutableMapOf<String, Any>(
        "http" to httpEvent
      ))
    }

    /**
     * Convert to map
     */
    fun toMap() = mapper.convertValue<Map<String, Any>>(
      this@FunctionConfig,
      mapTypeRef
    ).toMutableMap().apply {
      remove("input")
      remove("output")
    }
  }

  companion object {
    internal val mapper = ObjectMapper()
    internal val mapTypeRef = object : TypeReference<Map<String, Any>>() {}
    internal val yaml = Yaml(DumperOptions().apply {
      indent = 2
      defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
      defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
    })
  }
}