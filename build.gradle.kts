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
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
  // detekt 2.x, published as `dev.detekt`; the older io.gitlab.arturbosch.detekt line
  // stops at 1.23.8 and cannot run on JDK 25. 2.x has no stable release yet.
  id("dev.detekt") version "2.0.0-alpha.6"
}


val releaseVersion = project.property("releaseVersion") as String
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")


group = "dk.lldata"
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

// Reproducible archives: without this the jars embed file timestamps and filesystem
// ordering, so two builds of the same commit produce different bytes.
tasks.withType<AbstractArchiveTask>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
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
    freeCompilerArgs.set(emptyList())
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

    // Lets `-Dkitchensink.write=true` reach the test JVM to regenerate the golden file.
    System.getProperty("kitchensink.write")?.let { systemProperty("kitchensink.write", it) }

    finalizedBy(jacocoTestReport)
  }

  jacocoTestCoverageVerification {
    dependsOn(test)
    violationRules {
      rule {
        // A floor, not a target. Set just under the current numbers so a real regression
        // fails the build while ordinary movement does not.
        limit {
          counter = "LINE"
          value = "COVEREDRATIO"
          minimum = "0.78".toBigDecimal()
        }
        limit {
          counter = "BRANCH"
          value = "COVEREDRATIO"
          minimum = "0.61".toBigDecimal()
        }
        limit {
          counter = "INSTRUCTION"
          value = "COVEREDRATIO"
          minimum = "0.70".toBigDecimal()
        }
      }
    }
  }

  check {
    dependsOn(jacocoTestCoverageVerification)
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

// Runs as part of `check`, gating at zero findings. Thresholds that do not suit a code
// generator are relaxed in the config rather than suppressed wholesale; see the comments
// there. No baseline: a baseline reports zero while hiding the debt.
detekt {
  buildUponDefaultConfig = true
  config.setFrom(file("config/detekt/detekt.yml"))
  basePath.set(rootDir)
}

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
        url.set("https://github.com/lldata/typescriptpoet")

        organization {
          name.set("LL Data ApS")
          url.set("https://lldata.dk")
        }

        issueManagement {
          system.set("GitHub")
          url.set("https://github.com/lldata/typescriptpoet/issues")
        }

        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://raw.githubusercontent.com/lldata/typescriptpoet/main/LICENSE.txt")
            distribution.set("repo")
          }
        }

        scm {
          url.set("https://github.com/lldata/typescriptpoet")
          connection.set("scm:https://github.com/lldata/typescriptpoet.git")
          developerConnection.set("scm:git@github.com:lldata/typescriptpoet.git")
        }

        developers {
          // The original author, whose work this continues.
          developer {
            id.set("kdubb")
            name.set("Kevin Wooten")
            email.set("kevin@outfoxx.io")
          }
          developer {
            id.set("lldata")
            name.set("Lasse Lindgard")
            email.set("lasse@lldata.dk")
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
