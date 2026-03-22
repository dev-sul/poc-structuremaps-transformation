package de.gematik.test.fhir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.hl7.fhir.r4.formats.IParser;
import org.hl7.fhir.r4.model.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

public class ResourceComparator {

  private final org.hl7.fhir.r4.formats.JsonParser fhirJsonParser;

  public ResourceComparator() {
    this.fhirJsonParser = new org.hl7.fhir.r4.formats.JsonParser();
    this.fhirJsonParser.setOutputStyle(IParser.OutputStyle.NORMAL);
  }

  public ComparisonDiff compare(Resource actual, Resource expected) {
    Objects.requireNonNull(actual, "actual resource must not be null");
    Objects.requireNonNull(expected, "expected resource must not be null");

    var actualJson = toCanonicalJson(actual);
    var expectedJson = toCanonicalJson(expected);

    var entries = new ArrayList<ComparisonDiff.DiffEntry>();
    compareElements("$", expectedJson, actualJson, JsonNull.INSTANCE, entries);
    return new ComparisonDiff(entries);
  }

  private JsonElement toCanonicalJson(Resource resource) {
    try {
      var json = fhirJsonParser.composeString(resource);
      var raw = JsonParser.parseString(json);
      return normalize(raw, "$");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to serialize resource for comparison", e);
    }
  }

  private JsonElement normalize(JsonElement element, String path) {
    if (element == null || element.isJsonNull()) {
      return JsonNull.INSTANCE;
    }

    if (element.isJsonObject()) {
      var normalized = new JsonObject();
      var object = element.getAsJsonObject();

      var sorted = new TreeMap<String, JsonElement>();
      for (var entry : object.entrySet()) {
        if (shouldIgnore(entry.getKey(), path)) {
          continue;
        }
        var childPath = path + "." + entry.getKey();
        sorted.put(entry.getKey(), normalize(entry.getValue(), childPath));
      }

      for (var entry : sorted.entrySet()) {
        normalized.add(entry.getKey(), entry.getValue());
      }
      return normalized;
    }

    if (element.isJsonArray()) {
      var normalizedArray = new JsonArray();
      var normalizedValues = new ArrayList<JsonElement>();
      var index = 0;
      for (var item : element.getAsJsonArray()) {
        normalizedValues.add(normalize(item, path + "[]" + index));
        index++;
      }
      normalizedValues.sort(Comparator.comparing(JsonElement::toString));
      for (var value : normalizedValues) {
        normalizedArray.add(value);
      }
      return normalizedArray;
    }

    return element.deepCopy();
  }

  private boolean shouldIgnore(String fieldName, String path) {
    if ("id".equals(fieldName)) {
      return true;
    }
    return path.endsWith(".meta") && ("versionId".equals(fieldName) || "lastUpdated".equals(fieldName));
  }

  private void compareElements(
      String path,
      JsonElement expected,
      JsonElement actual,
      JsonElement parentContext,
      List<ComparisonDiff.DiffEntry> entries
  ) {
    expected = defaultIfNull(expected);
    actual = defaultIfNull(actual);

    if (expected.getClass() != actual.getClass()) {
      addDiff(entries, path, ComparisonDiff.Category.TYPE_MISMATCH, expected, actual, parentContext);
      return;
    }

    if (expected.isJsonObject()) {
      compareObjects(path, expected.getAsJsonObject(), actual.getAsJsonObject(), entries);
      return;
    }

    if (expected.isJsonArray()) {
      compareArrays(path, expected.getAsJsonArray(), actual.getAsJsonArray(), parentContext, entries);
      return;
    }

    if (!expected.equals(actual)) {
      addDiff(entries, path, ComparisonDiff.Category.VALUE_MISMATCH, expected, actual, parentContext);
    }
  }

  private JsonElement defaultIfNull(JsonElement element) {
    return element == null ? JsonNull.INSTANCE : element;
  }

  private void compareObjects(
      String path,
      JsonObject expectedObject,
      JsonObject actualObject,
      List<ComparisonDiff.DiffEntry> entries
  ) {
    for (var entry : expectedObject.entrySet()) {
      var key = entry.getKey();
      var childPath = path + "." + key;
      if (!actualObject.has(key)) {
        addDiff(entries, childPath, ComparisonDiff.Category.MISSING_FIELD, entry.getValue(), JsonNull.INSTANCE, expectedObject);
        continue;
      }
      compareElements(childPath, entry.getValue(), actualObject.get(key), expectedObject, entries);
    }

    for (var entry : actualObject.entrySet()) {
      var key = entry.getKey();
      if (!expectedObject.has(key)) {
        var childPath = path + "." + key;
        addDiff(entries, childPath, ComparisonDiff.Category.UNEXPECTED_FIELD, JsonNull.INSTANCE, entry.getValue(), actualObject);
      }
    }
  }

  private void compareArrays(
      String path,
      JsonArray expectedArray,
      JsonArray actualArray,
      JsonElement parentContext,
      List<ComparisonDiff.DiffEntry> entries
  ) {
    if (expectedArray.size() != actualArray.size()) {
      addDiff(entries, path, ComparisonDiff.Category.ARRAY_LENGTH_MISMATCH, expectedArray, actualArray, parentContext);
    }

    var max = Math.max(expectedArray.size(), actualArray.size());
    for (var index = 0; index < max; index++) {
      var expectedItem = index < expectedArray.size() ? expectedArray.get(index) : JsonNull.INSTANCE;
      var actualItem = index < actualArray.size() ? actualArray.get(index) : JsonNull.INSTANCE;
      compareElements(path + "[" + index + "]", expectedItem, actualItem, parentContext, entries);
    }
  }

  private void addDiff(
      List<ComparisonDiff.DiffEntry> entries,
      String path,
      ComparisonDiff.Category category,
      JsonElement expected,
      JsonElement actual,
      JsonElement parentContext
  ) {
    entries.add(new ComparisonDiff.DiffEntry(
        path,
        category,
        expected == null ? null : expected.toString(),
        actual == null ? null : actual.toString(),
        parentContext == null ? null : parentContext.toString()
    ));
  }
}

