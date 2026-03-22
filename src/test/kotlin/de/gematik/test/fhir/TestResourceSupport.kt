package de.gematik.test.fhir

import org.hl7.fhir.r4.formats.JsonParser
import org.hl7.fhir.r4.formats.XmlParser
import org.hl7.fhir.r4.model.Resource
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.notExists

internal object TestResourceSupport {
    private const val WRITE_ARTIFACTS_PROPERTY = "writeArtifacts"
    private const val WRITE_ARTIFACTS_ENV = "WRITE_ARTIFACTS"
    private const val SUREFIRE_MARKER_PROPERTY = "surefire.real.class.path"

    private val xmlParser = XmlParser()
    private val jsonParser = JsonParser()
    private val projectDir: Path = System.getProperty("basedir")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of(System.getProperty("user.dir"))
    private val buildDir: Path = System.getProperty("project.build.directory")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: projectDir.resolve("target")

    @Throws(IOException::class)
    fun readXmlResource(path: Path): Resource =
        xmlParser.parse(Files.readString(path, StandardCharsets.UTF_8))

    @Throws(IOException::class)
    fun readJsonResource(path: Path): Resource =
        jsonParser.parse(Files.readString(path, StandardCharsets.UTF_8))

    @Throws(IOException::class)
    fun loadFourSourceResources(baseDir: Path): List<Resource> =
        Files.walk(baseDir).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter(::isXmlOrJsonFile)
                .sorted(compareBy(Path::toString))
                .map(::readResource)
                .toList()
        }

    private fun isXmlOrJsonFile(path: Path): Boolean =
        path.extension.lowercase(Locale.ROOT) in setOf("xml", "json")

    private fun readResource(path: Path): Resource =
        try {
            if (path.extension.lowercase(Locale.ROOT) == "xml") {
                readXmlResource(path)
            } else {
                readJsonResource(path)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("Unable to read test resource: $path", exception)
        }

    fun buildArtifactPath(fileName: String): Path = buildDir.resolve(fileName)

    @Throws(IOException::class)
    fun writeResource(resource: Resource, outputPath: Path) {
        if (!isWriteArtifactsEnabled()) return
        outputPath.parent
            ?.takeIf(Path::notExists)
            ?.let(Files::createDirectories)
        Files.writeString(outputPath, jsonParser.composeString(resource), StandardCharsets.UTF_8)
    }

    private fun isWriteArtifactsEnabled(): Boolean =
        System.getProperty(WRITE_ARTIFACTS_PROPERTY)?.trim()?.equals("true", ignoreCase = true) ?: false ||
            System.getenv(WRITE_ARTIFACTS_ENV)?.trim()?.equals("true", ignoreCase = true) ?: false

}

