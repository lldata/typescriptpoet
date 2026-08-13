import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  `java-library`
  jacoco
  `maven-publish`
  signing

  kotlin("jvm") version "2.4.10"
  id("org.jetbrains.dokka") version "2.2.0"

  id("com.diffplug.spotless") version "8.9.0"
}


val releaseVersion = project.property("releaseVersion") as String
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")


group = "io.outfoxx"
version = releaseVersion
description = "A Kotlin/Java API for generating .ts source files."


//
// DEPENDENCIES
//

// Versions

val junitVersion = "6.1.3"
val hamcrestVersion = "3.0"

repositories {
  mavenCentral()
}

dependencies {

  //
  // TESTING
  //

  // junit
  testImplementation(platform("org.junit:junit-bom:$junitVersion"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")

}


//
// COMPILE
//

// Build with current tooling, but emit a library that asks as little as possible of its
// consumers.
//
//   - Java 8 bytecode, so any JVM 8+ consumer can load it.
//   - Kotlin language/API version 2.0, which is the floor: the 2.4 compiler rejects 1.9
//     outright ("API version 1.9 is no longer supported"). Metadata is therefore readable
//     by any Kotlin 2.0+ compiler rather than requiring 2.4.
//   - A kotlin-stdlib floor of 2.0.21 rather than 2.4.10. Gradle resolves to the highest
//     version any participant asks for, so a low floor constrains nobody; it just avoids
//     dragging every consumer up to the version we happened to build with.
kotlin {
  jvmToolchain(17)

  coreLibrariesVersion = "2.0.21"

  compilerOptions {
    languageVersion = KotlinVersion.KOTLIN_2_0
    apiVersion = KotlinVersion.KOTLIN_2_0
    jvmTarget = JvmTarget.JVM_1_8
    freeCompilerArgs.add("-Xjdk-release=1.8")
  }
}

java {
  withSourcesJar()
}

// There is no Java source, but the Kotlin plugin still cross-checks the two compile tasks'
// targets, so they have to agree.
tasks.compileJava {
  options.release.set(8)
}

tasks.compileTestJava {
  options.release.set(17)
}

// The tests are not published, so they are free to use current everything. JUnit 6
// requires Java 17, which is why this is not simply inherited from `main`.
tasks.compileTestKotlin {
  compilerOptions {
    languageVersion = KotlinVersion.DEFAULT
    apiVersion = KotlinVersion.DEFAULT
    jvmTarget = JvmTarget.JVM_17
    freeCompilerArgs.set(emptyList<String>())
  }
}


//
// TEST
//

jacoco {
  toolVersion = "0.8.15"
}

tasks {
  test {
    useJUnitPlatform()

    finalizedBy(jacocoTestReport)
  }

  jacocoTestReport {
    dependsOn(test)
    reports {
      // XML is what coverage services and CI summaries consume; HTML is for humans.
      xml.required.set(true)
      html.required.set(true)
    }
  }
}


//
// DOCS
//

dokka {
  dokkaPublications.html {
    outputDirectory.set(layout.buildDirectory.dir("javadoc/$releaseVersion"))
  }
}

// Maven Central requires a `-javadoc` artifact; Dokka's HTML output stands in for it,
// because `java { withJavadocJar() }` produces an empty jar for a Kotlin-only source set.
val javadocJar = tasks.register<Jar>("javadocJar") {
  archiveClassifier.set("javadoc")
  from(tasks.named("dokkaGeneratePublicationHtml"))
}


//
// CHECKS
//

spotless {
  kotlin {
    ktlint()
    licenseHeaderFile(rootProject.file("HEADER.txt"))
  }
}


//
// PUBLISHING
//

publishing {

  publications {

    create<MavenPublication>("library") {
      from(components["java"])
      artifact(javadocJar)

      pom {

        name.set("TypeScript Poet")
        description.set("TypeScriptPoet is a Kotlin and Java API for generating .ts source files.")
        url.set("https://github.com/outfoxx/typescriptpoet")

        organization {
          name.set("Outfox, Inc.")
          url.set("https://outfoxx.io")
        }

        issueManagement {
          system.set("GitHub")
          url.set("https://github.com/outfoxx/typescriptpoet/issues")
        }

        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://raw.githubusercontent.com/outfoxx/typescriptpoet/main/LICENSE.txt")
            distribution.set("repo")
          }
        }

        scm {
          url.set("https://github.com/outfoxx/typescriptpoet")
          connection.set("scm:https://github.com/outfoxx/typescriptpoet.git")
          developerConnection.set("scm:git@github.com:outfoxx/typescriptpoet.git")
        }

        developers {
          developer {
            id.set("kdubb")
            name.set("Kevin Wooten")
            email.set("kevin@outfoxx.io")
          }
        }

      }
    }

  }

  repositories {

    // OSSRH (oss.sonatype.org) was retired in 2025; these are the Central Portal endpoints.
    maven {
      name = "MavenCentral"
      val snapshotUrl = "https://central.sonatype.com/repository/maven-snapshots/"
      val releaseUrl = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
      url = uri(if (isSnapshot) snapshotUrl else releaseUrl)

      credentials {
        username = project.findProperty("ossrhUsername")?.toString()
        password = project.findProperty("ossrhPassword")?.toString()
      }
    }

  }

}

signing {
  if (!hasProperty("signing.keyId")) {
    useInMemoryPgpKeys(
      project.findProperty("signingKeyId")?.toString(),
      project.findProperty("signingKey")?.toString(),
      project.findProperty("signingPassword")?.toString()
    )
  }
  sign(publishing.publications["library"])
}

tasks.withType<Sign>().configureEach {
  onlyIf { !isSnapshot }
}


//
// RELEASING
//

tasks {

  register("publishMavenRelease") {
    dependsOn(
      "publishAllPublicationsToMavenCentralRepository"
    )
  }

}
