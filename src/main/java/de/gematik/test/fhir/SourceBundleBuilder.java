package de.gematik.test.fhir;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class SourceBundleBuilder {

  Bundle build(List<Resource> sources) {
    Objects.requireNonNull(sources, "sources must not be null");

    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    for (var source : sources) {
      if (source == null) {
        continue;
      }
      appendResource(bundle, source);
    }

    return bundle;
  }

  private void appendResource(Bundle target, Resource source) {
    if (appendDocumentEntriesIfApplicable(target, source) || appendParameterEntriesIfApplicable(target, source)) {
      return;
    }

    addAsEntry(target, source, null);
  }

  private boolean appendDocumentEntriesIfApplicable(Bundle target, Resource source) {
    if (!(source instanceof Bundle nestedBundle) || nestedBundle.getType() != Bundle.BundleType.DOCUMENT) {
      return false;
    }

    for (var entry : nestedBundle.getEntry()) {
      if (entry.hasResource()) {
        addAsEntry(target, entry.getResource(), entry.getFullUrl());
      }
    }
    return true;
  }

  private boolean appendParameterEntriesIfApplicable(Bundle target, Resource source) {
    if (!(source instanceof Parameters parameters)) {
      return false;
    }

    for (var parameter : parameters.getParameter()) {
      for (var part : parameter.getPart()) {
        if (part.hasResource()) {
          addAsEntry(target, part.getResource(), null);
        }
      }
    }
    return true;
  }

  private void addAsEntry(Bundle target, Resource resource, String fullUrl) {
    var entry = target.addEntry();
    entry.setResource(resource);

    if (fullUrl != null && !fullUrl.isBlank()) {
      entry.setFullUrl(fullUrl);
      return;
    }

    if (resource.hasIdElement() && resource.getIdElement().hasIdPart()) {
      entry.setFullUrl("urn:uuid:" + resource.getIdElement().getIdPart());
    } else {
      entry.setFullUrl("urn:uuid:" + UUID.randomUUID());
    }
  }
}

