package de.gematik.test.fhir;

import lombok.val;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class TransformerTest {

  private static final Path BASE_DIR = Path.of("src/test/resources/t-rezept");
  private static final Path SOURCE_DIR = BASE_DIR.resolve("source");
  private static final String CARBON_COPY_STRUCTURE_MAP =
      "https://gematik.de/fhir/erp-t-prescription/StructureMap/ERPTPrescriptionStructureMapCarbonCopy";

  @Test
  void transformFromXmlSourcesAndCompareToExpected() throws Exception {
    val transformer = new StrukturedMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP);
    val comparator = new ResourceComparator();

    val sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR);
    val transformed = transformer.transform(sources);

    Assertions.assertNotNull(transformed, "Transform result must not be null");
    Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource");

    val outcome = transformer.validate(transformed);
    val fatals = outcome.getIssue().stream()
        .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .count();
    Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues: " + outcome.getIssue());

    val expected = TestResourceSupport.readJsonResource(BASE_DIR.resolve("example-case-01-digitaler-durchschlag.json"));
    val diff = comparator.compare(transformed, expected);

    TestResourceSupport.maybeWriteResource(transformed, Path.of("target", "transform-result.json"));

    Assertions.assertNotNull(diff);
    Assertions.assertNotNull(diff.summary());
    Assertions.assertFalse(diff.summary().isBlank());
  }


  @Test
  void transformFromNestedSourceDirectory() throws Exception {
    val transformer = new StrukturedMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP);

    Assertions.assertTrue(Files.isDirectory(SOURCE_DIR), "Expected source directory with XML/JSON examples");

    val sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR);
    Assertions.assertEquals(4, sources.size(), "Expected exactly four source resources in t-rezept/source");

    val transformed = transformer.transform(sources);
    Assertions.assertNotNull(transformed, "Transform result from source directory must not be null");
    Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource");

    val outcome = transformer.validate(transformed);
    val fatals = outcome.getIssue().stream()
        .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .count();
    Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues: " + outcome.getIssue());
  }

  @Test
  void rejectsBlankStructureMapUrl() {
    val exception = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new StrukturedMapTransformer(FhirEngineFactory.createDefault(), "   ")
    );

    Assertions.assertTrue(exception.getMessage().contains("structureMapUrl"));
  }
}

