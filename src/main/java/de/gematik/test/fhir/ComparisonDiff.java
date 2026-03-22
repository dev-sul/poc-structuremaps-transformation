package de.gematik.test.fhir;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public record ComparisonDiff(List<DiffEntry> entries) {

  public enum Category {
    TYPE_MISMATCH,
    VALUE_MISMATCH,
    MISSING_FIELD,
    UNEXPECTED_FIELD,
    ARRAY_LENGTH_MISMATCH
  }

  public record DiffEntry(
      String path,
      Category category,
      String expected,
      String actual,
      String parentContext
  ) {
    public DiffEntry {
      Objects.requireNonNull(path, "path must not be null");
      Objects.requireNonNull(category, "category must not be null");
    }
  }

  public ComparisonDiff {
    entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
  }

  public boolean isEqual() {
    return entries.isEmpty();
  }

  public String summary() {
    if (isEqual()) {
      return "No differences found.";
    }

    var counts = new EnumMap<Category, Integer>(Category.class);
    for (var entry : entries) {
      counts.merge(entry.category(), 1, Integer::sum);
    }

    var builder = new StringBuilder();
    builder.append("Differences found: ").append(entries.size());
    for (var category : Category.values()) {
      var value = counts.get(category);
      if (value != null && value > 0) {
        builder.append(" | ").append(category.name()).append('=').append(value);
      }
    }
    return builder.toString();
  }
}

