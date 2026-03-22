package de.gematik.test.fhir;

import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.validation.ValidationEngine;

public final class FhirEngineFactory {

  public static final String DEFAULT_CORE_PACKAGE = "hl7.fhir.r4.core#4.0.1";

  private FhirEngineFactory() {
  }

  public static ValidationEngine createDefault() {
    return createWithIgs(DEFAULT_CORE_PACKAGE, "/de.gematik.erp.t-prescription-1.1.0-snapshots.tgz");
  }

  public static ValidationEngine createWithIgs(String corePackage, String igResourcePath) {
    corePackage = requireNonBlank(corePackage, "corePackage");
    igResourcePath = requireNonBlank(igResourcePath, "igResourcePath");

    try {
      var engine = new ValidationEngine.ValidationEngineBuilder().fromSource(corePackage);
      try (var stream = FhirEngineFactory.class.getResourceAsStream(igResourcePath)) {
        if (stream == null) {
          throw new IllegalArgumentException("IG package resource not found: " + igResourcePath);
        }
        var npmPackage = NpmPackage.fromPackage(stream);
        engine.getIgLoader().loadPackage(npmPackage, true);
      }
      return engine;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize ValidationEngine", e);
    }
  }

  private static String requireNonBlank(String value, String name) {
    var sanitized = java.util.Objects.requireNonNull(value, name + " must not be null").strip();
    if (sanitized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return sanitized;
  }
}

