import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  `java-library`
  jacoco

  kotlin("jvm") version "2.4.10"
  id("org.jetbrains.dokka") version "2.2.0"

  // Speaks the Central Portal's publisher API directly. The OSSRH compatibility endpoint
  // this replaced is for namespaces migrated off the legacy servers; `dk.lldata` was
  // registered on the Portal, so uploads through it were accepted and then went nowhere.
  // The plugin applies `maven-publish` and `signing` itself.
  id("com.vanniktech.maven.publish") version "0.37.0"

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

// The sources jar comes from the publishing plugin's KotlinJvm configuration below, so
// `withSourcesJar()` here would build a second one.

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

// Read once, at configuration time. CI supplies these as ORG_GRADLE_PROJECT_<name>
// environment variables rather than `-P` flags, so the values never reach the Gradle
// command line, where they would sit in the runner's process table and in the workflow
// log's echoed `Run` line.
// Only the signing properties. The plugin checks the Central credentials itself, in
// `prepareMavenCentralPublishing`, and says plainly which one is missing.
val signingCredentials = listOf(
  "signingInMemoryKey",
  "signingInMemoryKeyPassword",
).associateWith { project.findProperty(it)?.toString() }

// Whether this build is able to sign at all. A local build, or one on a service that builds
// from git such as JitPack, has no key and does not need one.
val canSign = signingCredentials.values.none { it.isNullOrBlank() }

// Central requires a `-javadoc` artifact, but not a large or useful one: nobody reads
// documentation out of a jar, and the full Dokka HTML site is already published to
// https://lldata.github.io/typescriptpoet/, which is where the KDoc link in the README points.
// Publishing that same 11 MB site as the Central artifact spends most of an 80 MB monthly
// budget on a copy nobody will open. This task produces a single-page stand-in that redirects
// there instead. `withJavadocJar()` was not used because it produces a genuinely empty jar for
// a Kotlin-only source set; a jar with real, if minimal, content is worth the few extra lines.
val minimalJavadocJar = tasks.register("minimalJavadocJar") {
  val outputDir = layout.buildDirectory.dir("minimal-javadoc")
  outputs.dir(outputDir)
  doLast {
    val dir = outputDir.get().asFile
    dir.mkdirs()
    dir.resolve("index.html").writeText(
      """
      <!doctype html>
      <meta charset="utf-8">
      <title>TypeScriptPoet API documentation</title>
      <meta http-equiv="refresh" content="0; url=https://lldata.github.io/typescriptpoet/">
      <link rel="canonical" href="https://lldata.github.io/typescriptpoet/">
      <p>The API documentation is published at
        <a href="https://lldata.github.io/typescriptpoet/">lldata.github.io/typescriptpoet</a>.</p>
      """.trimIndent()
    )
  }
}

mavenPublishing {

  // Deliberately not `automaticRelease = true`: the upload lands in the Portal and waits
  // for a human to press Publish. A released version is permanent and cannot be recalled,
  // so it stays a deliberate act rather than a consequence of pushing a tag.
  publishToMavenCentral()

  // Snapshots are not signed, and Central does not ask them to be. Neither is a build that
  // has no key to sign with: JitPack builds a commit as an ordinary version, and a
  // publication that *declares* `.asc` artifacts it cannot produce fails at publish time
  // complaining that `module.json.asc` does not exist, which describes the symptom rather
  // than the cause. Excluding the `sign` task is not enough, because the artifacts stay
  // declared.
  //
  // This cannot quietly ship an unsigned release: publishing to Central without a key is
  // refused below.
  if (!isSnapshot && canSign) {
    signAllPublications()
  }

  // `JavadocJar.Dokka` only means "zip up this task's output" — it works for any task, not
  // just a Dokka one, which is what lets `minimalJavadocJar` above stand in for it.
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Dokka(minimalJavadocJar),
      sourcesJar = true,
    )
  )

  coordinates("dk.lldata", "typescriptpoet", releaseVersion)

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
      connection.set("scm:git:https://github.com/lldata/typescriptpoet.git")
      developerConnection.set("scm:git:ssh://git@github.com/lldata/typescriptpoet.git")
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

// Name the signing property that is empty rather than letting the signing plugin complain
// about key format, which describes the symptom of an unset repository secret and not its
// cause. This is where it surfaces, because signing runs before the upload.
tasks.withType<Sign>().configureEach {
  doFirst {
    val missing = signingCredentials.filterValues { it.isNullOrBlank() }.keys
    check(missing.isEmpty()) {
      "Cannot publish $releaseVersion: no value for ${missing.joinToString()}. " +
        "CI supplies these from repository secrets as ORG_GRADLE_PROJECT_<name>."
    }
  }
}

// Signing is not configured at all when there is no key, so the check above never runs in
// that case — which is right for a local or JitPack build, and wrong for a release. The
// Portal rejects an unsigned bundle during validation, long after the tag was pushed, so
// refuse before anything is uploaded and name the property that is empty.
//
// This hangs off `prepareMavenCentralPublishing` rather than `publishToMavenCentral`, which
// would be too late: a task's `doFirst` runs after the tasks it depends on, and the upload is
// one of those.
tasks.matching { it.name == "prepareMavenCentralPublishing" }.configureEach {
  doFirst {
    check(canSign || isSnapshot) {
      val missing = signingCredentials.filterValues { it.isNullOrBlank() }.keys
      "Cannot publish $releaseVersion to Maven Central unsigned: no value for " +
        "${missing.joinToString()}. CI supplies these from repository secrets as " +
        "ORG_GRADLE_PROJECT_<name>."
    }
  }
}
