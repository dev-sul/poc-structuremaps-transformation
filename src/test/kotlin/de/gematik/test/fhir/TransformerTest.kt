package de.gematik.test.fhir

import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TransformerTest {
    companion object {
        private val transformer = StructureMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP)
        private val BASE_DIR: Path = Path.of("src/test/resources/t-rezept")
        private val SOURCE_DIR: Path = BASE_DIR.resolve("source")
        private val VALID_RESULT_PATH: Path = BASE_DIR.resolve("valid-example-case-01-digitaler-durchschlag.json")
        private val INVALID_RESULT_PATH: Path = BASE_DIR.resolve("invalid-example-case-01-digitaler-durchschlag.json")
        private const val CARBON_COPY_STRUCTURE_MAP: String =
            "https://gematik.de/fhir/erp-t-prescription/StructureMap/ERPTPrescriptionStructureMapCarbonCopy"
    }

    @Test
    fun validateExamples() {
        val resource = TestResourceSupport.readJsonResource(VALID_RESULT_PATH)
        val validation = transformer.validate(resource)
        val fatals = validation.issue.count { issue ->
            issue.severity == OperationOutcome.IssueSeverity.FATAL
        }
        Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues for transformed resource")
    }


    @Test
    fun transformExample() {
        val comparator = ResourceComparator()

        Assertions.assertTrue(Files.isDirectory(SOURCE_DIR), "Expected source directory with XML/JSON examples")

        val sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR)
        Assertions.assertEquals(4, sources.size, "Expected exactly four source resources in t-rezept/source")

        val transformed = transformFromSourceDirectory()
        TestResourceSupport.writeResource(transformed, TestResourceSupport.buildArtifactPath("transform-result.json"))
        val validation = transformer.validate(transformed)
        val fatals = validation.issue.count { issue ->
            issue.severity == OperationOutcome.IssueSeverity.FATAL
        }
        val validExpected = TestResourceSupport.readJsonResource(VALID_RESULT_PATH)
        val diff = comparator.compare(transformed, validExpected)

        Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues for transformed resource")
        Assertions.assertTrue(diff.isEqual(), diff.summary())
    }

    @Test
    fun transformFromNestedSourceDirectoryDoesNotMatchInvalidReference() {
        val transformed = transformFromSourceDirectory()
        val invalidExpected = TestResourceSupport.readJsonResource(INVALID_RESULT_PATH)
        val diff = ResourceComparator().compare(transformed, invalidExpected)

        Assertions.assertFalse(diff.isEqual(), "Transformed resource must not match invalid reference example")
        Assertions.assertFalse(diff.entries.isEmpty(), "Expected at least one diff against the invalid reference example")
    }

    @Test
    fun rejectsBlankStructureMapUrl() {
        val exception = Assertions.assertThrows(IllegalArgumentException::class.java) {
            StructureMapTransformer(FhirEngineFactory.createDefault(), "   ")
        }

        Assertions.assertTrue(exception.message.orEmpty().contains("structureMapUrl"))
    }

    private fun transformFromSourceDirectory(): Resource = transformer.run {
        val transformed = transform(TestResourceSupport.loadFourSourceResources(SOURCE_DIR))

        Assertions.assertNotNull(transformed, "Transform result must not be null")
        Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource")

        val parameters = transformed as Parameters
        val rxPrescription = parameters.parameter.firstOrNull { it.name == "rxPrescription" }
        Assertions.assertNotNull(rxPrescription, "Expected rxPrescription parameter in transformed Parameters resource")

        val prescriptionSignatureDate = rxPrescription!!.part.firstOrNull { it.name == "prescriptionSignatureDate" }
            ?: rxPrescription.addPart().setName("prescriptionSignatureDate")
        prescriptionSignatureDate.value = InstantType("2026-04-01T08:23:12Z")

        TestResourceSupport.writeResource(transformed, TestResourceSupport.buildArtifactPath("transform-result.json"))

        transformed
    }

}
