dependencies {
  "implementation"(kotlin("reflect"))
  "implementation"(gradleApi())
  "implementation"(project(":serverless-annotations"))
  "implementation"("com.fasterxml.jackson.core:jackson-core:2.9.6")
  "implementation"("com.fasterxml.jackson.core:jackson-databind:2.9.6")
  "implementation"("javax.validation:validation-api:1.1.0.Final")
  "implementation"("org.freemarker","freemarker","2.3.28")
  "implementation"("org.yaml","snakeyaml","1.21")
  "implementation"("org.reflections","reflections","0.9.11")
}
