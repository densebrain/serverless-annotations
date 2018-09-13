package org.densebrain.serverless

import freemarker.template.Configuration
import freemarker.template.Template
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.io.StringWriter

open class ServerlessBuilderTask : DefaultTask() {

  @Option(option = "documentation", description = "Whether or not to generate documentation")
  open var generateDocumentation: Boolean = false

  @Option(option = "documentationOutputFile", description = "An optional output file to write the documentation to as opposed to embedding it")
  open var documentationOutputFile: File? = null

  @Option(option = "extraModelClassNames", description = "Extra models to include")
  open var extraModelClassNames: Array<String> = arrayOf()

  @Option(option = "excludeRegex", description = "Extra models to include")
  open var excludeRegex: Array<String> = arrayOf()

  @Option(option = "excludeModelRegex", description = "Extra models to include")
  open var excludeModelRegex: Array<String> = arrayOf()

  @Option(option = "excludeFunctionRegex", description = "Extra models to include")
  open var excludeFunctionRegex: Array<String> = arrayOf()

  @Option(option = "archive", description = "Base package to scan")
  open var basePackage: String? = null

  @Option(option = "archive", description = "The archive to scan, should be a shadowed jar")
  open var archive: File? = null

  @Option(option = "outputFile", description = "Where to write the resulting Yaml file")
  open var outputFile: File? = null

  @Option(option = "serverlessConfigFile", description = "Template for serverless generation")
  open var serverlessConfigFile: File? = null


  private val functionBuilder = ServerlessFunctionBuilder(project)

  /**
   * Template configuration context
   */
  private val templateCfg = Configuration(Configuration.VERSION_2_3_28).apply {
    setClassLoaderForTemplateLoading(javaClass.classLoader,"")
  }

  /**
   * Get the SLS template to use
   */
  private fun getServerlessTemplate():Template {
    return templateCfg.getTemplate("/DefaultTemplate.ftl")
  }

  /**
   * Get template processed content
   */
  private fun getServerlessTemplateContent():String {
    val writer = StringWriter()
    getServerlessTemplate().process(getTemplateDataModel(),writer)
    return writer.buffer.toString()
  }

  /**
   * Generate template data model
   */
  private fun getTemplateDataModel(): Map<String,Any> = mapOf(
    "project" to project,
    "archive" to archive!!.absolutePath
  )

  /**
   * Get the serverless config if using the template
   */
  private fun getServerlessConfig():Map<String,Any> =
    Yaml().load<Map<String,Any>>(getServerlessTemplateContent())


  /**
   * Run the task
   */
  @TaskAction
  @Suppress("UNCHECKED_CAST")
  open fun run() {
    require(archive != null && archive?.exists() == true) {"Archive path is required: ${archive?.absolutePath ?: "null"}"}
    require(outputFile != null) {"Output path is required: ${outputFile?.absolutePath ?: "null"}"}
    require(basePackage != null) {"base package is required: ${basePackage ?: "null"}"}

    val outputFile = outputFile!!
    val archive = archive!!
    val basePackage = basePackage!!

    // CONFIGURE
    functionBuilder.generateDocumentation = generateDocumentation
    functionBuilder.extraModelClassNames = extraModelClassNames
    functionBuilder.excludeRegex = excludeRegex
    functionBuilder.excludeModelRegex = excludeModelRegex
    functionBuilder.excludeFunctionRegex = excludeFunctionRegex

    // GET THE BASE CONFIG
    val baseConfig = when {
      serverlessConfigFile == null -> getServerlessConfig()
      else -> Yaml().load<Map<String,Any>>(serverlessConfigFile!!.readText())
    }

    // PREPARE TO MAKE UPDATES
    val config = baseConfig.toMutableMap()

    // SCAN PACKAGES FOR FUNCTIONS
    val functionConfigs = functionBuilder.getFunctionConfigs(archive, basePackage)

    // UPDATE FUNCTIONS CONFIG
    val functions:MutableMap<Any,Any> = when {
      config["functions"] is Map<*, *> -> (baseConfig["functions"] as Map<Any,Any>).toMutableMap()
      else -> mutableMapOf()
    }

    // ADD DISCOVERED FUNCTIONS
    functions.putAll(functionConfigs)
    config["functions"] = functions

    // OUTPUT YAML
    val yaml = Yaml(DumperOptions().apply {
      indent = 2
      defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
      defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
    })

    // OUTPUT DOCUMENTATION
    if (generateDocumentation) {
      val documentation = functionBuilder.getDocumentationModels()
      if (documentationOutputFile == null) {
        val custom = (config["custom"] ?: run {
          config["custom"] = mutableMapOf<String, Any>()
          config["custom"]
        }) as MutableMap<String, Any>

        custom["documentation"] = documentation
      } else {
        logger.quiet("Writing documentation ${documentationOutputFile!!.absolutePath}")
        documentationOutputFile!!.delete()
        documentationOutputFile!!.writeText(yaml.dump(documentation))
      }
    }

    logger.quiet("Writing ${outputFile.absolutePath}")
    outputFile.delete()
    outputFile.writeText(yaml.dump(config))

  }


}