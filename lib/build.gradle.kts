import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    classpath("com.github.jengelman.gradle.plugins:shadow:2.0.4")
  }
}

plugins {
  base
  java
  kotlin("jvm") version "1.2.61"
}

subprojects {

  apply(plugin = "java")
  apply(plugin = "kotlin")

  group = "org.densebrain"
  version = "1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  dependencies {
    "implementation"(kotlin("stdlib-jdk8"))
    "testImplementation"("junit", "junit", "4.12")
  }

  configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
  }
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
  }
}
