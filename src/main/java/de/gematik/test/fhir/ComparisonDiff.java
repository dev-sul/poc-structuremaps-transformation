package de.gematik.test.fhir;

import lombok.val;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ComparisonDiff {

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

  private final List<DiffEntry> entries;

  public ComparisonDiff(List<DiffEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  public List<DiffEntry> entries() {
    return Collections.unmodifiableList(entries);
  }

  public boolean isEqual() {
    return entries.isEmpty();
  }

  public String summary() {
    if (isEqual()) {
      return "No differences found.";
    }

    val counts = new EnumMap<Category, Integer>(Category.class);
    for (val entry : entries) {
      counts.merge(entry.category(), 1, Integer::sum);
    }

    val builder = new StringBuilder();
    builder.append("Differences found: ").append(entries.size());
    for (val category : Category.values()) {
      val value = counts.get(category);
      if (value != null && value > 0) {
        builder.append(" | ").append(category.name()).append('=').append(value);
      }
    }
    return builder.toString();
  }
}

