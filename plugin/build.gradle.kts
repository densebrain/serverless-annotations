import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  kotlin("jvm") version "1.2.61"
}

group = "org.densebrain"
version = "1.0-SNAPSHOT"

val reflectionsVersion by extra { "0.9.11" }

repositories {
  mavenCentral()
}

dependencies {
  gradleApi()
  implementation("org.reflections:reflections:${reflectionsVersion}")
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}