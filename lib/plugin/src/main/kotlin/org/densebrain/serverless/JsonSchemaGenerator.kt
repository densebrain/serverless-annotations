package org.densebrain.serverless

import org.densebrain.serverless.annotations.JsonSchemaDefault
import org.densebrain.serverless.annotations.JsonSchemaDescription
import org.densebrain.serverless.annotations.JsonSchemaFormat
import org.densebrain.serverless.annotations.JsonSchemaTitle
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.jsonFormatVisitors.*
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import javax.validation.constraints.*

/**
 * Created by Roee Shlomo on 11/29/2016.
 *
 * Forked from Scala code by mbknor @ https://github.com/mbknor/mbknor-jackson-jsonSchema
 */

/**
 * Json Schema Generator
 * @param rootObjectMapper pre-configured ObjectMapper
 * @param debug Default = false - set to true if generator should log some debug info while generating the schema
 * @param config default = vanillaJsonSchemaDraft4. Please use html5EnabledSchema if generating HTML5 GUI, e.g. using https://github.com/jdorn/json-editor
 */
class JsonSchemaGenerator @JvmOverloads constructor(
  val rootObjectMapper: ObjectMapper,
  val config: JsonSchemaConfig = JsonSchemaConfig.vanillaJsonSchemaDraft4,
  val debug: Boolean = false,
  val propertiesAnnotationsToSkip: Set<String> = setOf(),
  val useExternalReferencing: Boolean = false
) {
  init {
    require((!useExternalReferencing) || (useExternalReferencing && !config.useTypeIdForDefinitionName)) { "Option useExternalReferencing CAN'T be combined with useTypeIdForDefinitionName option" }
  }

  companion object {
    @JvmStatic
    val JSON_SCHEMA_DRAFT_4_URL = "http://json-schema.org/draft-04/schema#"
  }

  fun generateExternalReference(clazz: Class<*>) = "{{model: ${clazz.simpleName}}}"

  open class MySerializerProvider {
    var _provider: SerializerProvider? = null

    fun setProvider(provider: SerializerProvider?) {
      _provider = provider
    }

    fun getProvider(): SerializerProvider {
      return _provider!!
    }
  }

  abstract class EnumSupport {
    abstract val _node: ObjectNode

    fun enumTypes(enums: MutableSet<String>?) {
      val enumValuesNode = JsonNodeFactory.instance.arrayNode()
      _node.set("enum", enumValuesNode)
      enums?.forEach {
        enumValuesNode.add(it)
      }
    }
  }

  private fun setFormat(node: ObjectNode, format: String): Unit {
    node.put("format", format)
  }

  data class DefinitionInfo(val ref: String?, val jsonObjectFormatVisitor: JsonObjectFormatVisitor?)

  data class WorkInProgress(val classInProgress: Class<*>, val nodeInProgress: ObjectNode)

  // Class that manages creating new definitions or getting $refs to existing definitions
  inner class DefinitionsHandler() {
    private var class2Ref = HashMap<Class<*>, String>()
    private val definitionsNode = JsonNodeFactory.instance.objectNode()

    // Used when 'combining' multiple invocations to getOrCreateDefinition when processing polymorphism.
    private var workInProgress: WorkInProgress? = null
    private var workInProgressStack: MutableList<WorkInProgress?> = mutableListOf()

    fun pushWorkInProgress(): Unit {
      workInProgressStack.add(workInProgressStack.count(), workInProgress)
      workInProgress = null
    }

    fun popworkInProgress(): Unit {
      val item = workInProgressStack.count() - 1
      workInProgress = workInProgressStack.removeAt(item)
    }

    fun getDefinitionName(clazz: Class<*>): String {
      return if (config.useTypeIdForDefinitionName) clazz.name else clazz.simpleName
    }

    // Either creates new definitions or return $ref to existing one
    fun getOrCreateDefinition(clazz: Class<*>,
                              objectDefinitionBuilder: (ObjectNode) -> JsonObjectFormatVisitor?)
      : DefinitionInfo {
      val ref = class2Ref[clazz]
      if (ref != null) {
        if (workInProgress != null) {
          // this is a recursive polymorphism call
          if (clazz != workInProgress!!.classInProgress)
            throw Exception("Wrong class - working on ${workInProgress!!.classInProgress} - got $clazz")
          return DefinitionInfo(null, objectDefinitionBuilder(workInProgress!!.nodeInProgress))
        }
        return DefinitionInfo(ref, null)
      }
      // new one - must build it
      var retryCount = 0
      var shortRef = getDefinitionName(clazz)
      var longRef = "#/definitions/" + shortRef
      var extRef = generateExternalReference(clazz)
      while (class2Ref.values.contains(longRef)) {
        retryCount += 1
        shortRef = clazz.simpleName + "_" + retryCount
        longRef = "#/definitions/" + clazz.simpleName + "_" + retryCount
        extRef = generateExternalReference(clazz) + "_" + retryCount
      }

      when (useExternalReferencing) {
        true -> class2Ref[clazz] = extRef
        false -> class2Ref[clazz] = longRef
      }

      // create definition
      val node = JsonNodeFactory.instance.objectNode()

      // When processing polymorphism, we might get multiple recursive calls to getOrCreateDefinition - this is a way to combine them
      workInProgress = WorkInProgress(clazz, node)

      when (useExternalReferencing) {
        true -> definitionsNode.set(extRef, node)
        false -> definitionsNode.set(shortRef, node)
      }

      val jsonObjectFormatVisitor = objectDefinitionBuilder.invoke(node)

      workInProgress = null

      return when (useExternalReferencing) {
        true -> DefinitionInfo(extRef, jsonObjectFormatVisitor)
        false -> DefinitionInfo(longRef, jsonObjectFormatVisitor)
      }
    }

    fun getFinalDefinitionsNode(): ObjectNode? {
      if (class2Ref.isEmpty())
        return null
      return definitionsNode
    }
  }

  data class PolymorphismInfo(val typePropertyName: String, val subTypeName: String)

  data class PropertyNode(val main: ObjectNode, val meta: ObjectNode)

  inner class MyJsonFormatVisitorWrapper
  (
    val objectMapper: ObjectMapper,
    val level: Int = 0,
    val node: ObjectNode = JsonNodeFactory.instance.objectNode(),
    val definitionsHandler: DefinitionsHandler,
    val currentProperty: BeanProperty? // This property may represent the BeanProperty when we're directly processing beneath the property
  ) : JsonFormatVisitorWrapper, MySerializerProvider() {

    inner open class MyJsonObjectFormatVisitor(var thisObjectNode: ObjectNode, var propertiesNode: ObjectNode) : JsonObjectFormatVisitor, MySerializerProvider() {
      // Used when rendering schema using propertyOrdering as specified here:
      // https://github.com/jdorn/json-editor#property-ordering
      var nextPropertyOrderIndex = 1

      fun myPropertyHandler(propertyName: String, propertyType: JavaType, prop: BeanProperty?, jsonPropertyRequired: Boolean): Unit {
        l("JsonObjectFormatVisitor - $propertyName: $propertyType")

        if (propertiesNode.get(propertyName) != null) {
          /*if (!config.disableWarnings) {
            log.warn(s"Ignoring property '$propertyName' in $propertyType since it has already been added, probably as type-property using polymorphism")
          }*/
          return
        }

        val propertyAnnotations = prop?.member?.annotations()?.toList()?.map { it -> it.annotationClass.simpleName }

        if (propertyAnnotations != null && propertiesAnnotationsToSkip.intersect(propertyAnnotations).isNotEmpty()) {
          return
        }

        // Need to check for Option/Optional-special-case before we know what node to use here.

        val thisPropertyNode: PropertyNode = {
          val thisPropertyNode = JsonNodeFactory.instance.objectNode()
          propertiesNode.set(propertyName, thisPropertyNode)

          if (config.usePropertyOrdering) {
            thisPropertyNode.put("propertyOrder", nextPropertyOrderIndex)
            nextPropertyOrderIndex += 1
          }

          // Check for Option/Optional-special-case
          // TODO: removed for Java6 compatibility
//                    if ( config.useOneOfForOption &&
//                            (    Optional::class.java.isAssignableFrom(propertyType.rawClass)) ) {
          if (config.useOneOfForOption &&
            propertyType.rawClass.name in arrayOf("java.util.Optional", "java8.util.Optional")) {
            // Need to special-case for property using Option/Optional
            // Should insert oneOf between 'real one' and 'null'
            val oneOfArray = JsonNodeFactory.instance.arrayNode()
            thisPropertyNode.set("oneOf", oneOfArray)


            // Create the one used when Option is empty
            val oneOfNull = JsonNodeFactory.instance.objectNode()
            oneOfNull.put("type", "null")
            oneOfNull.put("title", "Not included")
            oneOfArray.add(oneOfNull)

            // Create the one used when Option is defined with the real value
            val oneOfReal = JsonNodeFactory.instance.objectNode()
            oneOfArray.add(oneOfReal)

            // Return oneOfReal which, from now on, will be used as the node representing this property
            PropertyNode(oneOfReal, thisPropertyNode)

          } else {
            // Not special-casing - using thisPropertyNode as is
            PropertyNode(thisPropertyNode, thisPropertyNode)
          }
        }.invoke()

        // Continue processing this property

        val childVisitor = createChild(thisPropertyNode.main, currentProperty = prop)

        // TODO: removed for Java6 compatibility
//                if( Optional::class.java.isAssignableFrom(propertyType.rawClass) && propertyType.containedTypeCount() >= 1) {
        if (propertyType.rawClass.name in arrayOf("java.util.Optional", "java8.util.Optional")
          && propertyType.containedTypeCount() >= 1) {
          // Property is Java Optional.
          //
          // Due to Java's Type Erasure, the type behind Option is lost.
          // To workaround this, we use the same workaround as jackson-scala-module described here:
          // https://github.com/FasterXML/jackson-module-scala/wiki/FAQ#deserializing-optionint-and-other-primitive-challenges

          val optionType: JavaType = resolveType(propertyType, prop, objectMapper)

          definitionsHandler.pushWorkInProgress()
          objectMapper.acceptJsonFormatVisitor(optionType, childVisitor)
          definitionsHandler.popworkInProgress()
        } else {
          definitionsHandler.pushWorkInProgress()
          objectMapper.acceptJsonFormatVisitor(propertyType, childVisitor)
          definitionsHandler.popworkInProgress()
        }

        // Check if we should set this property as required
        val rawClass = propertyType.rawClass
        val requiredProperty: Boolean = if (rawClass.isPrimitive) {
          // primitive boolean MUST have a value
          true
        } else if (jsonPropertyRequired) {
          // @JsonPropertyRequired is set to true
          true
        } else if (prop?.getAnnotation(NotNull::class.java) != null) {
          true
        } else {
          false
        }

        if (requiredProperty) {
          getRequiredArrayNode(thisObjectNode).add(propertyName)
        }

        if (prop != null) {
          resolvePropertyFormat(prop)?.let {
            setFormat(thisPropertyNode.main, it)
          }

          // Optionally add description
          prop.getAnnotation(JsonSchemaDescription::class.java)?.let {
            thisPropertyNode.meta.put("description", it.value)
          }
          prop.getAnnotation(JsonPropertyDescription::class.java)?.let {
            thisPropertyNode.meta.put("description", it.value)
          }

          // Optionally add description
          prop.getAnnotation(JsonSchemaTitle::class.java)?.let {
            thisPropertyNode.meta.put("title", it.value)
          }
          if (config.autoGenerateTitleForProperties &&
            !thisPropertyNode.meta.has("title")) {
            // We should generate 'pretty-name' based on propertyName
            thisPropertyNode.meta.put("title", generateTitleFromPropertyName(propertyName))
          }
        }
      }

      override fun property(writer: BeanProperty?) {
        l("JsonObjectFormatVisitor.property: prop:$writer")
        if (writer != null) {
          myPropertyHandler(writer.name, writer.type, writer, jsonPropertyRequired = true)
        }
      }

      override fun property(name: String, handler: JsonFormatVisitable?, propertyTypeHint: JavaType) {
        l("JsonObjectFormatVisitor.property: name:$name handler:$handler propertyTypeHint:$propertyTypeHint")
        myPropertyHandler(name, propertyTypeHint, null, jsonPropertyRequired = true)
      }

      override fun optionalProperty(writer: BeanProperty?) {
        l("JsonObjectFormatVisitor.optionalProperty: prop:$writer")
        if (writer != null) {
          myPropertyHandler(writer.name, writer.type, writer, jsonPropertyRequired = false)
        }
      }

      override fun optionalProperty(name: String, handler: JsonFormatVisitable?, propertyTypeHint: JavaType) {
        l("JsonObjectFormatVisitor.optionalProperty: name:$name handler:$handler propertyTypeHint:$propertyTypeHint")
        myPropertyHandler(name, propertyTypeHint, null, jsonPropertyRequired = false)
      }

    }

    fun l(s: String): Unit {
      if (!debug) return

      var indent = ""
      for (i in 0..level) {
        indent += "  "
      }
      println(indent + s)
    }

    fun createChild(childNode: ObjectNode, currentProperty: BeanProperty?): MyJsonFormatVisitorWrapper {
      return MyJsonFormatVisitorWrapper(objectMapper, level + 1, node = childNode, definitionsHandler = definitionsHandler, currentProperty = currentProperty)
    }

    override fun expectStringFormat(type: JavaType?): JsonStringFormatVisitor {
      l("expectStringFormat - _type: $type")

      node.put("type", "string")

      if (currentProperty != null) {
        // Look for @Pattern
        currentProperty.getAnnotation(Pattern::class.java)?.let {
          node.put("pattern", it.regexp)
        }

        // Look for @ai.mapper.ktjsonschema.JsonSchemaDefault
        currentProperty.getAnnotation(JsonSchemaDefault::class.java)?.let {
          node.put("default", it.value)
        }

        // Look for @Size
        currentProperty.getAnnotation(Size::class.java)?.let {
          if (it.min > 0) {
            node.put("minLength", it.min)
          }
          if (it.max != Integer.MAX_VALUE) {
            node.put("maxLength", it.max)
          }
        }

        // If we did not find @Size - check if we should include it anyway
        if (config.useMinLengthForNotNull &&
          !node.has("minLength") && !node.has("maxLength")) {
          currentProperty.getAnnotation(NotNull::class.java)?.let {
            node.put("minLength", 1)
          }
        }
      }

      return object : JsonStringFormatVisitor, EnumSupport() {
        override val _node: ObjectNode
          get() = node

        override fun format(format: JsonValueFormat?) {
          setFormat(node, format.toString())
        }
      }
    }

    override fun expectArrayFormat(_type: JavaType?): JsonArrayFormatVisitor {
      l("expectArrayFormat - _type: $_type")

      node.put("type", "array")

      config.defaultArrayFormat?.let {
        setFormat(node, it)
      }

      val itemsNode = JsonNodeFactory.instance.objectNode()
      node.set("items", itemsNode)

      // We get improved result while processing kotlin-collections by getting elementType this way
      // instead of using the one which we receive in JsonArrayFormatVisitor.itemsFormat
      // This approach also works for Java
      val preferredElementType: JavaType? = _type!!.contentType

      return object : JsonArrayFormatVisitor, MySerializerProvider() {

        override fun itemsFormat(handler: JsonFormatVisitable?, elementType: JavaType?) {
          l("expectArrayFormat - handler: $handler - elementType: $elementType - preferredElementType: $preferredElementType")
          objectMapper.acceptJsonFormatVisitor(preferredElementType
            ?: elementType, createChild(itemsNode, currentProperty = null))
        }

        override fun itemsFormat(format: JsonFormatTypes?) {
          l("itemsFormat - format: $format")
          if (format != null) {
            itemsNode.put("type", format.value())
          }
        }
      }
    }

    override fun expectNullFormat(type: JavaType?): JsonNullFormatVisitor {
      l("expectNullFormat - _type: $type")
      return object : JsonNullFormatVisitor {}
    }

    override fun expectNumberFormat(type: JavaType?): JsonNumberFormatVisitor {
      l("expectNumberFormat")

      node.put("type", "number")

      // Look for @Min, @Max => minumum, maximum
      currentProperty?.let {
        it.getAnnotation(Min::class.java)?.let {
          node.put("minimum", it.value)
        }
        it.getAnnotation(Max::class.java)?.let {
          node.put("maximum", it.value)
        }
        it.getAnnotation(JsonSchemaDefault::class.java)?.let {
          node.put("default", it.value.toLong())
        }
      }

      return object : JsonNumberFormatVisitor, EnumSupport() {
        override val _node: ObjectNode
          get() = node

        override fun format(format: JsonValueFormat?) {
          setFormat(node, format.toString())
        }

        override fun numberType(type: JsonParser.NumberType?) {
          l("JsonNumberFormatVisitor.numberType: $type")
        }
      }
    }

    override fun expectAnyFormat(type: JavaType?): JsonAnyFormatVisitor {
      return object : JsonAnyFormatVisitor {}
    }

    override fun expectMapFormat(type: JavaType?): JsonMapFormatVisitor {
      l("expectMapFormat - _type: $type")

      // There is no way to specify map in jsonSchema,
      // So we're going to treat it as type=object with additionalProperties = true,
      // so that it can hold whatever the map can hold


      val additionalPropsObject = JsonNodeFactory.instance.objectNode()

      node.put("type", "object")
      node.set("additionalProperties", additionalPropsObject)

      // TODO: this is from latest mbknor - is it better?
//            definitionsHandler.pushWorkInProgress()
//            val childVisitor = createChild(additionalPropsObject, null)
//            objectMapper.acceptJsonFormatVisitor(type!!.containedType(1), childVisitor)
//            definitionsHandler.popworkInProgress()

      return object : JsonMapFormatVisitor, MySerializerProvider() {
        override fun valueFormat(handler: JsonFormatVisitable?, valueType: JavaType?) {
          l("JsonMapFormatVisitor.valueFormat handler: $handler - valueType: $valueType")
          objectMapper.acceptJsonFormatVisitor(valueType, createChild(additionalPropsObject, currentProperty = null))
        }

        override fun keyFormat(handler: JsonFormatVisitable?, keyType: JavaType?) {
          l("JsonMapFormatVisitor.keyFormat handler: $handler - keyType: $keyType")
          if (keyType != null) {
            if (!keyType.isTypeOrSubTypeOf(String::class.java)) {
              node.put("additionalProperties", true)
            }
          }
        }
      }
    }

    override fun expectIntegerFormat(type: JavaType?): JsonIntegerFormatVisitor {
      node.put("type", "integer")

      // Look for @Min, @Max => minumum, maximum
      currentProperty?.let {
        it.getAnnotation(Min::class.java)?.let {
          node.put("minimum", it.value)
        }
        it.getAnnotation(Max::class.java)?.let {
          node.put("maximum", it.value)
        }
        it.getAnnotation(JsonSchemaDefault::class.java)?.let {
          node.put("default", it.value.toInt())
        }
      }
      return object : JsonIntegerFormatVisitor, EnumSupport() {
        override val _node: ObjectNode
          get() = node

        override fun format(format: JsonValueFormat?) {
          setFormat(node, format.toString())
        }

        override fun numberType(type: JsonParser.NumberType?) {
          l("JsonIntegerFormatVisitor.numberType: $type")
        }
      }
    }

    override fun expectBooleanFormat(type: JavaType?): JsonBooleanFormatVisitor {
      l("expectBooleanFormat")

      node.put("type", "boolean")

      currentProperty?.let {
        // Look for @ai.mapper.ktjsonschema.JsonSchemaDefault
        it.getAnnotation(JsonSchemaDefault::class.java)?.let {
          node.put("default", it.value.toBoolean())
        }
      }

      return object : JsonBooleanFormatVisitor, EnumSupport() {
        override val _node: ObjectNode
          get() = node

        override fun format(format: JsonValueFormat?) {
          setFormat(node, format.toString())
        }
      }
    }

    private fun getRequiredArrayNode(objectNode: ObjectNode): ArrayNode {
      if (objectNode.has("required")) {
        val node = objectNode.get("required")
        if (node is ArrayNode)
          return node
      }
      val rn = JsonNodeFactory.instance.arrayNode()
      objectNode.set("required", rn)
      return rn
    }

    private fun extractPolymorphismInfo(_type: JavaType): PolymorphismInfo? {
      // look for @JsonTypeInfo
      val ac = AnnotatedClass.construct(_type, objectMapper.deserializationConfig)
      val jsonTypeInfo: JsonTypeInfo? = ac.annotations?.get(JsonTypeInfo::class.java)

      if (jsonTypeInfo != null) {
        if (jsonTypeInfo.include != JsonTypeInfo.As.PROPERTY)
          throw Exception("We only support polymorphism using jsonTypeInfo.include() == JsonTypeInfo.As.PROPERTY")
        if (jsonTypeInfo.use != JsonTypeInfo.Id.NAME)
          throw Exception("We only support polymorphism using jsonTypeInfo.use == JsonTypeInfo.Id.NAME")

        val propertyName = jsonTypeInfo.property
        val subTypeName: String = objectMapper.subtypeResolver
          .collectAndResolveSubtypesByClass(objectMapper.deserializationConfig, ac)
          .filter { it.type == _type.rawClass }
          .find { it -> true } // find first
          ?.name!!
        return PolymorphismInfo(propertyName, subTypeName)
      }
      return null
    }

    override fun expectObjectFormat(_type: JavaType): JsonObjectFormatVisitor? {
      val ac = AnnotatedClass.construct(_type, objectMapper.deserializationConfig)
      val resolvedSubTypes = objectMapper.subtypeResolver.collectAndResolveSubtypesByClass(objectMapper.deserializationConfig, ac)

      val subTypes = resolvedSubTypes.map { it -> it.type }.filter { it ->
        _type.rawClass.isAssignableFrom(it) && _type.rawClass != it
      }

      if (subTypes.isNotEmpty()) {
        //l(s"polymorphism - subTypes: $subTypes")

        val anyOfArrayNode = JsonNodeFactory.instance.arrayNode()
        node.set("oneOf", anyOfArrayNode)

        subTypes.forEach {
          val subType = it

          l("polymorphism - subType: $subType")

          val definitionInfo: DefinitionInfo = definitionsHandler.getOrCreateDefinition(subType, {
            val childVisitor = createChild(it, currentProperty = null)
            objectMapper.acceptJsonFormatVisitor(subType, childVisitor)
            null
          })

          val thisOneOfNode = JsonNodeFactory.instance.objectNode()
          thisOneOfNode.put("\$ref", definitionInfo.ref)
          anyOfArrayNode.add(thisOneOfNode)

        }

        return null // Returning null to stop jackson from visiting this object since we have done it manually

      } else {

        val objectBuilder: (ObjectNode) -> JsonObjectFormatVisitor? = { thisObjectNode ->
          {
            thisObjectNode.put("type", "object")
            thisObjectNode.put("additionalProperties", false)

            // If class is annotated with ai.mapper.ktjsonschema.JsonSchemaFormat, we should add it
            val ac_i = AnnotatedClass.construct(_type, objectMapper.deserializationConfig)
            resolvePropertyFormat(_type, objectMapper)?.let {
              setFormat(thisObjectNode, it)
            }

            // If class is annotated with ai.mapper.ktjsonschema.JsonSchemaDescription, we should add it
            ac_i.annotations.get(JsonSchemaDescription::class.java)?.let {
              thisObjectNode.put("description", it.value)
            }
            ac_i.annotations.get(JsonPropertyDescription::class.java)?.let {
              thisObjectNode.put("description", it.value)
            }

            // If class is annotated with ai.mapper.ktjsonschema.JsonSchemaTitle, we should add it
            ac_i.annotations.get(JsonSchemaTitle::class.java)?.let {
              thisObjectNode.put("title", it.value)
            }

            val propertiesNode = JsonNodeFactory.instance.objectNode()
            thisObjectNode.set("properties", propertiesNode)

            extractPolymorphismInfo(_type)?.let {
              val pi = it

              // This class is a child in a polymorphism config..
              // Set the title = subTypeName
              thisObjectNode.put("title", pi.subTypeName)

              // must inject the 'type'-param and value as enum with only one possible value
              val enumValuesNode = JsonNodeFactory.instance.arrayNode()
              enumValuesNode.add(pi.subTypeName)

              val enumObjectNode = JsonNodeFactory.instance.objectNode()
              enumObjectNode.put("type", "string")
              enumObjectNode.set("enum", enumValuesNode)
              enumObjectNode.put("default", pi.subTypeName)

              if (config.hidePolymorphismTypeProperty) {
                // Make sure the editor hides this polymorphism-specific property
                val optionsNode = JsonNodeFactory.instance.objectNode()
                enumObjectNode.set("options", optionsNode)
                optionsNode.put("hidden", true)
              }

              propertiesNode.set(pi.typePropertyName, enumObjectNode)

              getRequiredArrayNode(thisObjectNode).add(pi.typePropertyName)
            }

            MyJsonObjectFormatVisitor(thisObjectNode, propertiesNode)
          }.invoke()
        }


        if (level == 0) {
          // This is the first level - we must not use definitions
          return objectBuilder(node)
        } else {
          val definitionInfo: DefinitionInfo = definitionsHandler.getOrCreateDefinition(_type.rawClass, objectBuilder)

          definitionInfo.ref?.let {
            // Must add ref to def at "this location"
            node.put("\$ref", it)
          }

          return definitionInfo.jsonObjectFormatVisitor
        }
      }
    }
  }

  fun generateTitleFromPropertyName(propertyName: String): String {
    // Code found here: http://stackoverflow.com/questions/2559759/how-do-i-convert-camelcase-into-human-readable-names-in-java
    val s = propertyName.replace(
      Regex(String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
      )),
      " "
    )

    // Make the first letter uppercase
    return s.substring(0, 1).toUpperCase() + s.substring(1)
  }

  fun resolvePropertyFormat(_type: JavaType, objectMapper: ObjectMapper): String? {
    val ac = AnnotatedClass.construct(_type, objectMapper.deserializationConfig)
    return resolvePropertyFormat(ac.getAnnotation(JsonSchemaFormat::class.java),
      _type.rawClass.name)
  }

  fun resolvePropertyFormat(prop: BeanProperty): String? {
    // Prefer format specified in annotation
    return resolvePropertyFormat(prop.getAnnotation(JsonSchemaFormat::class.java),
      prop.type.rawClass.name)
  }

  fun resolvePropertyFormat(jsonSchemaFormatAnnotation: JsonSchemaFormat?, rawClassName: String): String? {
    // Prefer format specified in annotation
    if (jsonSchemaFormatAnnotation != null)
      return jsonSchemaFormatAnnotation.value
    else
      return config.customType2FormatMapping[rawClassName]
  }

  @Suppress("DEPRECATION")
  fun resolveType(propertyType: JavaType, prop: BeanProperty?, objectMapper: ObjectMapper): JavaType {
    if (prop != null) {
      prop.getAnnotation(JsonDeserialize::class.java)?.let {
        return objectMapper.typeFactory.uncheckedSimpleType(it.contentAs.java)
      }
    }
    return propertyType.containedType(0)
  }


  fun <T> generateJsonSchema(clazz: Class<T>): JsonNode =
    generateJsonSchema(clazz, null, null)

  fun <T> generateJsonSchema(clazz: Class<T>, title: String?, description: String?): JsonNode {
    val rootNode = JsonNodeFactory.instance.objectNode()

    // Specify that this is a v4 json schema
    rootNode.put("\$schema", JSON_SCHEMA_DRAFT_4_URL)
    //rootNode.put("id", "http://my.site/myschema#")

    // Add schema title
    if (title == null) {
      rootNode.put("title", generateTitleFromPropertyName(clazz.simpleName))
    } else {
      rootNode.put("title", title)
    }

    // Maybe set schema description
    description?.let {
      rootNode.put("description", it)
      // If root class is annotated with @ai.mapper.ktjsonschema.JsonSchemaDescription, it will later override this description
    }


    val definitionsHandler = DefinitionsHandler()
    val rootVisitor = MyJsonFormatVisitorWrapper(rootObjectMapper, node = rootNode, definitionsHandler = definitionsHandler, currentProperty = null)
    rootObjectMapper.acceptJsonFormatVisitor(clazz, rootVisitor)

    definitionsHandler.getFinalDefinitionsNode()?.let {
      if (!useExternalReferencing) {
        rootNode.set("definitions", it)
      }
    }

    return rootNode
  }

}