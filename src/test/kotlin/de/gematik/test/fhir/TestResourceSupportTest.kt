package de.gematik.test.fhir

import org.hl7.fhir.r4.model.Parameters
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TestResourceSupportTest {
    @Test
    fun maybeWriteResourceSkipsOutputInsideSurefireByDefault(@TempDir tempDir: Path) {
        withSystemProperties(
            WRITE_ARTIFACTS_PROPERTY to null,
            SUREFIRE_MARKER_PROPERTY to "present",
        ) {
            val outputPath = tempDir.resolve("transform-result.json")

            TestResourceSupport.maybeWriteResource(Parameters().apply { id = "default-disabled" }, outputPath)

            assertFalse(Files.exists(outputPath))
        }
    }

    @Test
    fun maybeWriteResourceWritesOutputWhenExplicitlyEnabled(@TempDir tempDir: Path) {
        withSystemProperties(
            WRITE_ARTIFACTS_PROPERTY to "true",
            SUREFIRE_MARKER_PROPERTY to "present",
        ) {
            val outputPath = tempDir.resolve("transform-result.json")

            TestResourceSupport.maybeWriteResource(Parameters().apply { id = "explicitly-enabled" }, outputPath)

            assertTrue(Files.exists(outputPath))
            assertTrue(Files.size(outputPath) > 0)
        }
    }

    private fun withSystemProperties(vararg overrides: Pair<String, String?>, block: () -> Unit) {
        val previousValues = overrides.associate { (key) -> key to System.getProperty(key) }

        try {
            overrides.forEach { (key, value) ->
                if (value == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, value)
                }
            }
            block()
        } finally {
            previousValues.forEach { (key, value) ->
                if (value == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, value)
                }
            }
        }
    }

    companion object {
        private const val WRITE_ARTIFACTS_PROPERTY = "writeArtifacts"
        private const val SUREFIRE_MARKER_PROPERTY = "surefire.real.class.path"
    }
}

