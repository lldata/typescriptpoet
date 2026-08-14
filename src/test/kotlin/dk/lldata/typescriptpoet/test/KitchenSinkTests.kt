/*
 * Copyright 2017 Outfox, Inc.
 * Copyright 2026 LL Data ApS
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
package dk.lldata.typescriptpoet.test

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

  /**
   * Both ends of the supported range, and the reason there are two rather than one.
   *
   * 5 is the floor: the newest construct the library can emit is a `const` type parameter,
   * which is TypeScript 5.0, so that is the oldest compiler the output can be held to. It
   * gates -- generated code that 5 rejects is a bug.
   *
   * 7 is the current major, and running it says the output has not been left behind. TypeScript
   * 7 is the native compiler rather than a language release, so it should accept exactly what
   * 5 does; if it ever stops, that is worth knowing before a consumer finds it. The likeliest
   * candidate is `--experimentalDecorators`, which the sink needs for its parameter decorator
   * and which the 6.x line exists to deprecate things like.
   *
   * Testing against 7 is not a claim that consumers need it.
   */
  @ParameterizedTest(name = "typescript@{0} accepts the golden file")
  @ValueSource(ints = [5, 7])
  fun testGoldenFileTypeChecks(typescriptMajor: Int, @TempDir dir: Path) {
    assumeTrue(npxAvailable(), "npx is not available; skipping the tsc type-check")

    val source = KitchenSink.emit()
    dir.resolve("kitchen-sink.ts").writeText(source)
    KitchenSink.companionModules.forEach { (name, contents) ->
      dir.resolve(name).writeText(contents)
    }

    val result = run(
      listOf(
        "npx", "-y", "-p", "typescript@$typescriptMajor", "tsc",
        "--noEmit", "--strict", "--target", "ES2022", "--lib", "ES2022,DOM",
        "--module", "ES2022", "--moduleResolution", "bundler",
        "--experimentalDecorators",
        dir.resolve("kitchen-sink.ts").toString(),
      ),
      timeoutSeconds = 300,
    )

    assertThat(
      "tsc $typescriptMajor rejected the generated source:\n\n$source\n\n${result.second}",
      result.first,
      equalTo(0),
    )
  }

  @Test
  @DisplayName("Prettier accepts the golden file unchanged")
  fun testGoldenFileIsPrettierClean() {
    assumeTrue(npxAvailable(), "npx is not available; skipping the Prettier check")

    val source = KitchenSink.emit()
    val file = dir2().resolve("kitchen-sink.ts")
    file.writeText(source)

    val result = run(
      listOf("npx", "-y", "prettier@3", "--check", file.toString()),
      timeoutSeconds = 300,
    )

    assertThat(
      "Prettier would reformat the generated source:\n\n$source\n\n${result.second}",
      result.first,
      equalTo(0),
    )
  }

  private fun dir2(): Path = kotlin.io.path.createTempDirectory("prettier-check")

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
