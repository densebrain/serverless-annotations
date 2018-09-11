package org.densebrain.serverless

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.densebrain.serverless.JsonSchemaGenerator



internal class JsonSchemaGeneratorTest {

  class TestModel(val myVal:Long)

  @Test
  fun generateJsonSchema() {
    val objectMapper = ObjectMapper()
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()

    val generator = JsonSchemaGenerator(objectMapper, JsonSchemaConfig.vanillaJsonSchemaDraft4, false, setOf("JsonIgnore"))
    val node = generator.generateJsonSchema(TestModel::class.java)
    println("Result: \n${objectWriter.writeValueAsString(node)}")
  }
}