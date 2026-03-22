package de.gematik.test.fhir;

import lombok.val;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class SourceBundleBuilder {

  Bundle build(List<Resource> sources) {
    Objects.requireNonNull(sources, "sources must not be null");

    val bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    for (val source : sources) {
      if (source == null) {
        continue;
      }
      appendResource(bundle, source);
    }

    return bundle;
  }

  private void appendResource(Bundle target, Resource source) {
    if (source instanceof Bundle nestedBundle && nestedBundle.getType() == Bundle.BundleType.DOCUMENT) {
      for (val entry : nestedBundle.getEntry()) {
        if (entry.hasResource()) {
          addAsEntry(target, entry.getResource(), entry.getFullUrl());
        }
      }
      return;
    }

    if (source instanceof Parameters parameters) {
      for (val parameter : parameters.getParameter()) {
        for (val part : parameter.getPart()) {
          if (part.hasResource()) {
            addAsEntry(target, part.getResource(), null);
          }
        }
      }
      return;
    }

    addAsEntry(target, source, null);
  }

  private void addAsEntry(Bundle target, Resource resource, String fullUrl) {
    val entry = target.addEntry();
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

