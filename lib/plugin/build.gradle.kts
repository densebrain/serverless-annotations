//import com.github.jengelman.gradle.plugins.shadow.PluginShadowPlugin
//import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
//import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

//plugins {
//  id("com.github.johnrengelman.shadow")
//}

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
//
//val shadowJar = tasks.getByName<ShadowJar>("shadowJar") {
//  archiveName = "${project.name}.jar"
//  classifier = ""
//}
//
//tasks.getByName("build").dependsOn(shadowJar)
//tasks.getByName("jar").dependsOn(shadowJar)
