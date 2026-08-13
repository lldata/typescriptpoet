import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `java-library`
  jacoco
  `maven-publish`
  signing

  kotlin("jvm") version "2.4.10"
  id("org.jetbrains.dokka") version "2.2.0"

  id("com.diffplug.spotless") version "8.9.0"
}


val releaseVersion: String by project
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

val javaVersion = 17

kotlin {
  jvmToolchain(javaVersion)

  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

java {
  withSourcesJar()
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
val javadocJar by tasks.registering(Jar::class) {
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
