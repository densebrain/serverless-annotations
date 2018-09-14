package org.densebrain.serverless

/**
 * Created by Roee Shlomo on 11/29/2016.
 *
 * Forked from Scala code by mbknor @ https://github.com/mbknor/mbknor-jackson-jsonSchema
 */
data class JsonSchemaConfig
(
  val autoGenerateTitleForProperties: Boolean,
  val defaultArrayFormat: String?,
  val useOneOfForOption: Boolean,
  val usePropertyOrdering: Boolean,
  val hidePolymorphismTypeProperty: Boolean,
  val disableWarnings: Boolean,
  val useMinLengthForNotNull: Boolean,
  val useTypeIdForDefinitionName: Boolean,
  val customType2FormatMapping: Map<String, String>
) {
  companion object {
    @JvmStatic
    val vanillaJsonSchemaDraft4 = JsonSchemaConfig(
      autoGenerateTitleForProperties = false,
      defaultArrayFormat = null,
      useOneOfForOption = false,
      usePropertyOrdering = false,
      hidePolymorphismTypeProperty = false,
      disableWarnings = false,
      useMinLengthForNotNull = false,
      useTypeIdForDefinitionName = false,
      customType2FormatMapping = mapOf(
        "java.lang.Long" to "int64",
        "Long" to "int64",
        "long" to "int64",
        "integer" to "in64"
      )
    )

    @JvmStatic
    val vanillaJsonSchemaDraft4WithIds = JsonSchemaConfig(
      autoGenerateTitleForProperties = false,
      defaultArrayFormat = null,
      useOneOfForOption = false,
      usePropertyOrdering = false,
      hidePolymorphismTypeProperty = false,
      disableWarnings = false,
      useMinLengthForNotNull = false,
      useTypeIdForDefinitionName = true,
      customType2FormatMapping = mapOf()
    )

    /**
     * Use this configuration if using the JsonSchema to generate HTML5 GUI, eg. by using https://github.com/jdorn/json-editor
     *
     * autoGenerateTitleForProperties - If property is named "someName", we will add {"title": "Some Name"}
     * defaultArrayFormat - this will result in a better gui than te default one.
     */
    @JvmStatic
    val html5EnabledSchema = JsonSchemaConfig(
      autoGenerateTitleForProperties = true,
      defaultArrayFormat = "table",
      useOneOfForOption = true,
      usePropertyOrdering = true,
      hidePolymorphismTypeProperty = true,
      disableWarnings = false,
      useMinLengthForNotNull = true,
      useTypeIdForDefinitionName = false,
      customType2FormatMapping = mapOf(
        "java.lang.Long" to "number",

        // Java7 dates
        "java.time.LocalDateTime" to "datetime-local",
        "java.time.OffsetDateTime" to "datetime",
        "java.time.LocalDate" to "date",
        // Joda-dates
        "org.joda.time.LocalDate" to "date"
      )
    )
  }
}