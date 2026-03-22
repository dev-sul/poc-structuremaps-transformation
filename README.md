# PoC StrutureMap Transformation 

This project provides an in-memory API to transform e-prescription source resources using StructureMaps and compare the resulting content.

## API

Package: `de.gematik.test.fhir`

- `new StrukturedMapTransformer(validationEngine, structureMapUrl)`
- `StrukturedMapTransformer.transform(List<Resource>) -> Resource`
- `StrukturedMapTransformer.validate(Resource) -> OperationOutcome`
- `new ResourceComparator().compare(Resource actual, Resource expected) -> ComparisonDiff`

Comparison rules (pragmatic):

- Array element order is ignored
- `id`, `meta.versionId`, and `meta.lastUpdated` are ignored
- Text fields and all other content are compared strictly

## Tests

The JUnit tests are located in:

- `src/test/java/de/gematik/test/fhir/TransformerTest.java`

Covered scenarios:

- Transformation from the source resources under `src/test/resources/t-rezept/source`
- FHIR validation of the transformed resource
- Comparison against `example-case-01-digitaler-durchschlag.json`
- Validation of the generic API by passing an explicit StructureMap URL

Optional artifact output (tests only):

- JVM property `writeArtifacts=true`
- writes `target/transform-result.json`

## Quick Start

```bash
mvn test
```

With artifact output:

```bash
mvn -DwriteArtifacts=true test
```

