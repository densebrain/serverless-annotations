package org.densebrain.serverless

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.*
import javax.validation.constraints.NotNull


internal class JsonSchemaGeneratorTest {

  enum class Permission {
    HELLO,
    GOODBYE
  }

  class TestModel(
    @NotNull
    val date: Date,
    val myInt:Int,
    val myLong:Long,
    val permissions:Array<Permission>,
    val randomUnusedObject:Any
  )

  @Test
  fun generateJsonSchema() {
    val objectMapper = ObjectMapper()
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()

    val generator = JsonSchemaGenerator(
      objectMapper,
      JsonSchemaConfig.vanillaJsonSchemaDraft4,
      false,
      setOf("JsonIgnore"),
      setOf(".*random.*")
    )
    val node = generator.generateJsonSchema(TestModel::class.java)
    println("Result: \n${objectWriter.writeValueAsString(node)}")
  }
}