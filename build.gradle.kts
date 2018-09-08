import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.densebrain"
version = "1.0-SNAPSHOT"


plugins {
  java
  kotlin("jvm") version "1.2.61"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}