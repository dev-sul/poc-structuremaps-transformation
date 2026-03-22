package de.gematik.test.fhir

import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TransformerTest {
    companion object {
        private val BASE_DIR: Path = Path.of("src/test/resources/t-rezept")
        private val SOURCE_DIR: Path = BASE_DIR.resolve("source")
        private const val CARBON_COPY_STRUCTURE_MAP: String =
            "https://gematik.de/fhir/erp-t-prescription/StructureMap/ERPTPrescriptionStructureMapCarbonCopy"
    }

    @Test
    fun transformFromNestedSourceDirectory() {
        Assertions.assertTrue(Files.isDirectory(SOURCE_DIR), "Expected source directory with XML/JSON examples")

        val sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR)
        Assertions.assertEquals(4, sources.size, "Expected exactly four source resources in t-rezept/source")

        val transformed = transformFromSourceDirectory()
        val fatals = createTransformer().validate(transformed).issue.count { issue ->
            issue.severity == OperationOutcome.IssueSeverity.FATAL || issue.severity == OperationOutcome.IssueSeverity.ERROR
        }

        Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues for transformed resource")
    }

    @Test
    fun rejectsBlankStructureMapUrl() {
        val exception = Assertions.assertThrows(IllegalArgumentException::class.java) {
            StrukturedMapTransformer(FhirEngineFactory.createDefault(), "   ")
        }

        Assertions.assertTrue(exception.message.orEmpty().contains("structureMapUrl"))
    }

    private fun transformFromSourceDirectory(): Resource = createTransformer().run {
        val transformed = transform(TestResourceSupport.loadFourSourceResources(SOURCE_DIR))

        Assertions.assertNotNull(transformed, "Transform result must not be null")
        Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource")

        TestResourceSupport.maybeWriteResource(transformed, TestResourceSupport.buildArtifactPath("transform-result.json"))

        transformed
    }


    private fun createTransformer() = StrukturedMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP)
}
