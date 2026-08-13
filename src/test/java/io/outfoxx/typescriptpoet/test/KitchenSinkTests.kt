/*
 * Copyright 2017 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.outfoxx.typescriptpoet.test

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/**
 * The integration test: one file using every construct the library can emit, compared
 * against a checked-in golden file, then handed to the real TypeScript compiler.
 *
 * The comparison normalises whitespace. What matters first is that the right *code* comes
 * out; layout is a separate concern, and pinning it here would make every formatting tweak
 * look like a behaviour change. `testGoldenFileMatchesExactly` is the stricter version,
 * disabled by default -- flip [ENFORCE_FORMATTING] to turn layout into a requirement too.
 *
 * Regenerate the golden file after an intended change:
 *
 *     ./gradlew test --tests '*KitchenSinkTests*' -Dkitchensink.write=true
 */
@DisplayName("Kitchen Sink Integration Tests")
class KitchenSinkTests {

  private companion object {

    /** Set to true to also require the emitted layout to match the golden file exactly. */
    const val ENFORCE_FORMATTING = false

    const val GOLDEN = "/kitchen-sink.ts"
  }

  @Test
  @DisplayName("Emits the golden file, ignoring whitespace")
  fun testGoldenFileMatches() {
    val actual = KitchenSink.emit()

    if (System.getProperty("kitchensink.write") == "true") {
      val target = Path.of("src/test/resources/kitchen-sink.ts")
      target.writeText(actual)
      println("Wrote golden file to ${target.toAbsolutePath()}")
      return
    }

    val expected = readGolden()

    assertThat(normalise(actual), equalTo(normalise(expected)))

    if (ENFORCE_FORMATTING) {
      assertThat(actual, equalTo(expected))
    }
  }

  @Test
  @DisplayName("tsc accepts the golden file")
  fun testGoldenFileTypeChecks(@TempDir dir: Path) {
    assumeTrue(npxAvailable(), "npx is not available; skipping the tsc type-check")

    val source = KitchenSink.emit()
    dir.resolve("kitchen-sink.ts").writeText(source)
    KitchenSink.companionModules.forEach { (name, contents) ->
      dir.resolve(name).writeText(contents)
    }

    val result = run(
      listOf(
        "npx", "-y", "-p", "typescript@5", "tsc",
        "--noEmit", "--strict", "--target", "ES2022", "--lib", "ES2022,DOM",
        "--module", "ES2022", "--moduleResolution", "bundler",
        "--experimentalDecorators",
        dir.resolve("kitchen-sink.ts").toString(),
      ),
      timeoutSeconds = 300,
    )

    assertThat(
      "tsc rejected the generated source:\n\n$source\n\n${result.second}",
      result.first,
      equalTo(0),
    )
  }

  /**
   * Collapses runs of whitespace so the comparison is about the code, not the layout.
   *
   * Safe for TypeScript, which is whitespace-insensitive outside string and template
   * literals -- and both sides are normalised identically, so a literal's internal spacing
   * still has to agree.
   */
  private fun normalise(source: String) = source.replace(Regex("\\s+"), " ").trim()

  private fun readGolden(): String = checkNotNull(KitchenSinkTests::class.java.getResourceAsStream(GOLDEN)) {
    "missing golden file $GOLDEN; regenerate with -Dkitchensink.write=true"
  }.bufferedReader().readText()

  private fun npxAvailable(): Boolean =
    runCatching { run(listOf("npx", "--version"), timeoutSeconds = 60).first == 0 }.getOrDefault(false)

  private fun run(command: List<String>, timeoutSeconds: Long): Pair<Int, String> {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      return -1 to "timed out after ${timeoutSeconds}s"
    }
    return process.exitValue() to output
  }
}
