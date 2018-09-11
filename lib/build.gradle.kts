import com.github.zafarkhaja.semver.Version
import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.bundling.Jar
import java.util.Date
import java.util.Properties
import java.io.StringReader
import Versions
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformSpec

buildscript {
  dependencies {
    classpath("com.github.zafarkhaja:java-semver:0.9.0")
    classpath("org.junit.platform:junit-platform-gradle-plugin:${Versions.junitPlatform}")
  }
}

plugins {
  base
  java
  `maven-publish`
  id("com.jfrog.bintray") version "1.8.4"
  kotlin("jvm") version "1.2.61"
}

// LOAD VERSION FILE AND PATCH INCREMENT
var VERSION = file("${rootDir}/../version.txt").readText()
VERSION = Version.valueOf(VERSION).incrementPatchVersion().toString()

// LOAD LOCAL PROPS FOR DISTRIBUTION
var binTrayKey = ""
val localPropsFile = file("${rootDir}/../local.properties")
if (localPropsFile.exists()) {
  val localProps = Properties().apply {
    load(StringReader(localPropsFile.readText()))
  }

  binTrayKey = localProps.getProperty("BINTRAY_API_KEY", "") as String
}

allprojects {
  group = "org.densebrain"
  version = VERSION
}

subprojects {

  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "maven-publish")
  apply(plugin = "com.jfrog.bintray")
  apply(plugin = "org.junit.platform.gradle.plugin")

//junitPlatform {
//    platformVersion = Versions.junitPlatform // optional, defaults to plugin version
//      reportsDir = file("${rootDir}/build/test-results/junit-platform") // this is the default
//  }

  repositories {
    mavenCentral()
  }



  dependencies {
    "implementation"(kotlin("stdlib-jdk8"))

    testRuntime(
      "org.junit.jupiter:junit-jupiter-engine:${Versions.junit}"
    )


    "testImplementation"(
      //"junit:junit:${Versions.junit}",
      "org.junit.jupiter:junit-jupiter-api:${Versions.junit}"
    )
  }

  /**
   * Configure java/kotlin source
   */
  configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8

    sourceSets {
      getByName("main") {
        java {
          srcDirs("src/main/kotlin")
        }
      }
    }
  }

  /**
   * Sources JAR for distro
   */
  val sourcesJar = tasks.create<Jar>("sourceJar") {
    classifier = "sources"
    from(sourceSets["main"].java.srcDirs)
  }

  /**
   * Artifacts (SOURCES)
   */
  artifacts {
    add("archives", sourcesJar)
  }


  /**
   * Publication name to be used between
   * maven-publish and bintray
   */
  val publicationName = "${project.name}-publication"

  bintray {
    user = "jonglanz"
    key = binTrayKey
    publish = true

    setPublications(publicationName)

    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {

      repo = "oss"
      name = this@subprojects.name
      userOrg = "densebrain"
      setLicenses("Apache-2.0")
      vcsUrl = "https://github.com/densebrain/serverless-annotations.git"
      version(delegateClosureOf<BintrayExtension.VersionConfig> {
        name = VERSION
        released = Date().toString()
      })
    })
  }

  configure<PublishingExtension> {
    publications.create<MavenPublication>(publicationName) {
      from(components["java"])
      groupId = this@subprojects.group as? String
      artifactId = this@subprojects.name
      version = VERSION
      artifact(sourcesJar)
    }
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
  }
}

tasks {
  val publishBintray = create("publishBintray") {
    dependsOn(":serverless-annotations:bintrayUpload",":serverless-plugin:bintrayUpload")
  }

  create("versionPatch") {
    finalizedBy(publishBintray)

    doLast {
      val versionFile = file("${rootDir}/../version.txt")

      versionFile.writeText(VERSION)

      project.exec {
        setCommandLine("git")
        setArgs(mutableListOf("add", "../version.txt"))
      }

      project.exec {
        setCommandLine("git")
        setArgs(mutableListOf("commit","-a", "-m", VERSION))
      }

      project.exec {
        setCommandLine("git")
        setArgs(mutableListOf("tag", VERSION))
      }

      project.exec {
        setCommandLine("git")
        setArgs(mutableListOf("push"))
      }

      project.exec {
        setCommandLine("git")
        setArgs(mutableListOf("push", "--tags"))
      }
    }
  }
}



