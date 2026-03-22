package de.gematik.test.fhir;

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
    var transformer = new StrukturedMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP);
    var comparator = new ResourceComparator();

    var sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR);
    var transformed = transformer.transform(sources);

    Assertions.assertNotNull(transformed, "Transform result must not be null");
    Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource");

    var outcome = transformer.validate(transformed);
    var fatals = outcome.getIssue().stream()
        .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .count();
    Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues: " + outcome.getIssue());

    var expected = TestResourceSupport.readJsonResource(BASE_DIR.resolve("example-case-01-digitaler-durchschlag.json"));
    var diff = comparator.compare(transformed, expected);

    TestResourceSupport.maybeWriteResource(transformed, Path.of("target", "transform-result.json"));

    Assertions.assertNotNull(diff);
    Assertions.assertNotNull(diff.summary());
    Assertions.assertFalse(diff.summary().isBlank());
  }


  @Test
  void transformFromNestedSourceDirectory() throws Exception {
    var transformer = new StrukturedMapTransformer(FhirEngineFactory.createDefault(), CARBON_COPY_STRUCTURE_MAP);

    Assertions.assertTrue(Files.isDirectory(SOURCE_DIR), "Expected source directory with XML/JSON examples");

    var sources = TestResourceSupport.loadFourSourceResources(SOURCE_DIR);
    Assertions.assertEquals(4, sources.size(), "Expected exactly four source resources in t-rezept/source");

    var transformed = transformer.transform(sources);
    Assertions.assertNotNull(transformed, "Transform result from source directory must not be null");
    Assertions.assertEquals("Parameters", transformed.fhirType(), "Expected Parameters output resource");

    var outcome = transformer.validate(transformed);
    var fatals = outcome.getIssue().stream()
        .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .count();
    Assertions.assertEquals(0, fatals, "FHIR validation returned fatal issues: " + outcome.getIssue());
  }

  @Test
  void rejectsBlankStructureMapUrl() {
    var exception = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new StrukturedMapTransformer(FhirEngineFactory.createDefault(), "   ")
    );

    Assertions.assertTrue(exception.getMessage().contains("structureMapUrl"));
  }
}

