package de.gematik.test.fhir;

import org.hl7.fhir.r4.formats.JsonParser;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.formats.JsonCreatorGson;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.utils.validation.constants.CheckDisplayOption;
import org.hl7.fhir.utilities.ByteProvider;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.ValidationEngine;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StrukturedMapTransformer {

  private final ValidationEngine validationEngine;
  private final String structureMapUrl;
  private final SourceBundleBuilder bundleBuilder;
  private final JsonParser r4JsonParser;

  public StrukturedMapTransformer(ValidationEngine validationEngine, String structureMapUrl) {
    this.validationEngine = Objects.requireNonNull(validationEngine, "validationEngine must not be null");
    this.structureMapUrl = requireStructureMapUrl(structureMapUrl);
    this.bundleBuilder = new SourceBundleBuilder();
    this.r4JsonParser = new JsonParser();
  }

  public Resource transform(List<Resource> sources) {
    try {
      var sourceBundle = bundleBuilder.build(sources);
      var inputJson = r4JsonParser.composeBytes(sourceBundle);

      var transformed = validationEngine.transform(
          ByteProvider.forBytes(inputJson),
          org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON,
          structureMapUrl
      );

      return parseR4Resource(transformed);
    } catch (Exception e) {
      throw new IllegalStateException("Transformation failed", e);
    }
  }

  public OperationOutcome validate(Resource resource) {
    Objects.requireNonNull(resource, "resource must not be null");
    try {
      var json = r4JsonParser.composeBytes(resource);
      var validator = validationEngine.getValidator(org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
      validator.setNoTerminologyChecks(true);
      validator.setValidateValueSetCodesOnTxServer(false);
      validator.setCheckDisplay(CheckDisplayOption.Ignore);
      validator.setUnknownCodeSystemsCauseErrors(false);

      var messages = new ArrayList<ValidationMessage>();
      try (var in = new ByteArrayInputStream(json)) {
        validator.validate(null, messages, in, org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
      }
      return toOutcome(messages);
    } catch (Exception e) {
      throw new IllegalStateException("Validation failed", e);
    }
  }

  private OperationOutcome toOutcome(List<ValidationMessage> messages) {
    var outcome = new OperationOutcome();
    for (var message : messages) {
      var issue = outcome.addIssue();
      issue.setSeverity(mapSeverity(message.getLevel()));
      issue.setCode(OperationOutcome.IssueType.INVALID);
      issue.setDiagnostics(message.summary());
      if (message.getLocation() != null) {
        issue.addLocation(message.getLocation());
      }
    }
    return outcome;
  }

  private String requireStructureMapUrl(String structureMapUrl) {
    var sanitizedStructureMapUrl = Objects.requireNonNull(structureMapUrl, "structureMapUrl must not be null").trim();
    if (sanitizedStructureMapUrl.isEmpty()) {
      throw new IllegalArgumentException("structureMapUrl must not be blank");
    }
    return sanitizedStructureMapUrl;
  }

  private OperationOutcome.IssueSeverity mapSeverity(ValidationMessage.IssueSeverity severity) {
    if (severity == null) {
      return OperationOutcome.IssueSeverity.INFORMATION;
    }

    return switch (severity) {
      case FATAL -> OperationOutcome.IssueSeverity.FATAL;
      case ERROR -> OperationOutcome.IssueSeverity.ERROR;
      case WARNING -> OperationOutcome.IssueSeverity.WARNING;
      default -> OperationOutcome.IssueSeverity.INFORMATION;
    };
  }

  private Resource parseR4Resource(Element transformed) {
    try (var out = new ByteArrayOutputStream();
         var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
      var creator = new JsonCreatorGson(writer);
      new org.hl7.fhir.r5.elementmodel.JsonParser(validationEngine.getContext()).compose(transformed, creator);
      creator.finish();
      writer.flush();
      return r4JsonParser.parse(out.toByteArray());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to parse transformed resource as R4", e);
    }
  }
}


