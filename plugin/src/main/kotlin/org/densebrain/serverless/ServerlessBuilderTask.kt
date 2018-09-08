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

  @Option(option = "archive", description = "The archive to scan, should be a shadowed jar")
  open var basePackage: String? = null

  @Option(option = "archive", description = "The archive to scan, should be a shadowed jar")
  open var archive: File? = null

  @Option(option = "outputFile", description = "Where to write the resulting Yaml file")
  open var outputFile: File? = null

  @Option(option = "serverlessTemplate", description = "Template for serverless generation")
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
  open fun run() {
    require(archive != null && archive?.exists() == true) {"Archive path is required: ${archive?.absolutePath ?: "null"}"}
    require(outputFile != null) {"Output path is required: ${outputFile?.absolutePath ?: "null"}"}
    require(basePackage != null) {"base package is required: ${basePackage ?: "null"}"}
    val outputFile = outputFile!!
    val archive = archive!!
    val basePackage = basePackage!!

    val baseConfig = when {
      serverlessConfigFile == null -> getServerlessConfig()
      else -> Yaml().load<Map<String,Any>>(serverlessConfigFile!!.readText())
    }

    val functionConfig = functionBuilder.getFunctionConfig(archive, basePackage)

    outputFile.delete()
    logger.quiet("Writing ${outputFile.absolutePath}")
    FileWriter(outputFile)
      .use { writer ->
        val yaml = Yaml(DumperOptions().apply {
          indent = 2
          defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
          defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
        })

        yaml.dump(baseConfig, writer)
      }

  }


}