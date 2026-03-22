package de.gematik.test.fhir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.val;
import org.hl7.fhir.r4.formats.JsonParserBase;
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
    this.fhirJsonParser.setOutputStyle(JsonParserBase.OutputStyle.NORMAL);
  }

  public ComparisonDiff compare(Resource actual, Resource expected) {
    Objects.requireNonNull(actual, "actual resource must not be null");
    Objects.requireNonNull(expected, "expected resource must not be null");

    val actualJson = toCanonicalJson(actual);
    val expectedJson = toCanonicalJson(expected);

    val entries = new ArrayList<ComparisonDiff.DiffEntry>();
    compareElements("$", expectedJson, actualJson, JsonNull.INSTANCE, entries);
    return new ComparisonDiff(entries);
  }

  private JsonElement toCanonicalJson(Resource resource) {
    try {
      val json = fhirJsonParser.composeString(resource);
      val raw = JsonParser.parseString(json);
      return normalize(raw, "$", "$");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to serialize resource for comparison", e);
    }
  }

  private JsonElement normalize(JsonElement element, String path, String fieldName) {
    if (element == null || element.isJsonNull()) {
      return JsonNull.INSTANCE;
    }

    if (element.isJsonObject()) {
      val normalized = new JsonObject();
      val object = element.getAsJsonObject();

      val sorted = new TreeMap<String, JsonElement>();
      for (val entry : object.entrySet()) {
        if (shouldIgnore(entry.getKey(), path)) {
          continue;
        }
        val childPath = path + "." + entry.getKey();
        sorted.put(entry.getKey(), normalize(entry.getValue(), childPath, entry.getKey()));
      }

      for (val entry : sorted.entrySet()) {
        normalized.add(entry.getKey(), entry.getValue());
      }
      return normalized;
    }

    if (element.isJsonArray()) {
      val normalizedArray = new JsonArray();
      val normalizedValues = new ArrayList<JsonElement>();
      var index = 0;
      for (val item : element.getAsJsonArray()) {
        normalizedValues.add(normalize(item, path + "[]" + index, fieldName));
        index++;
      }
      normalizedValues.sort(Comparator.comparing(JsonElement::toString));
      for (val value : normalizedValues) {
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
    if (expected == null) {
      expected = JsonNull.INSTANCE;
    }
    if (actual == null) {
      actual = JsonNull.INSTANCE;
    }

    if (expected.getClass() != actual.getClass()) {
      addDiff(entries, path, ComparisonDiff.Category.TYPE_MISMATCH, expected, actual, parentContext);
      return;
    }

    if (expected.isJsonObject()) {
      val expectedObject = expected.getAsJsonObject();
      val actualObject = actual.getAsJsonObject();

      for (val entry : expectedObject.entrySet()) {
        val key = entry.getKey();
        val childPath = path + "." + key;
        if (!actualObject.has(key)) {
          addDiff(entries, childPath, ComparisonDiff.Category.MISSING_FIELD, entry.getValue(), JsonNull.INSTANCE, expectedObject);
          continue;
        }
        compareElements(childPath, entry.getValue(), actualObject.get(key), expectedObject, entries);
      }

      for (val entry : actualObject.entrySet()) {
        val key = entry.getKey();
        if (!expectedObject.has(key)) {
          val childPath = path + "." + key;
          addDiff(entries, childPath, ComparisonDiff.Category.UNEXPECTED_FIELD, JsonNull.INSTANCE, entry.getValue(), actualObject);
        }
      }
      return;
    }

    if (expected.isJsonArray()) {
      val expectedArray = expected.getAsJsonArray();
      val actualArray = actual.getAsJsonArray();

      if (expectedArray.size() != actualArray.size()) {
        addDiff(entries, path, ComparisonDiff.Category.ARRAY_LENGTH_MISMATCH, expectedArray, actualArray, parentContext);
      }

      val max = Math.max(expectedArray.size(), actualArray.size());
      for (var i = 0; i < max; i++) {
        val expectedItem = i < expectedArray.size() ? expectedArray.get(i) : JsonNull.INSTANCE;
        val actualItem = i < actualArray.size() ? actualArray.get(i) : JsonNull.INSTANCE;
        compareElements(path + "[" + i + "]", expectedItem, actualItem, parentContext, entries);
      }
      return;
    }

    if (!expected.equals(actual)) {
      addDiff(entries, path, ComparisonDiff.Category.VALUE_MISMATCH, expected, actual, parentContext);
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

