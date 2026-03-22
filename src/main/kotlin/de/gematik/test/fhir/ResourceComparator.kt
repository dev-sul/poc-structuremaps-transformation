package de.gematik.test.fhir

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.hl7.fhir.r4.formats.IParser
import org.hl7.fhir.r4.model.Resource
import java.io.IOException

class ResourceComparator {
    private val fhirJsonParser = org.hl7.fhir.r4.formats.JsonParser().apply {
        outputStyle = IParser.OutputStyle.NORMAL
    }

    fun compare(actual: Resource, expected: Resource): ComparisonDiff {
        val actualJson = toCanonicalJson(actual)
        val expectedJson = toCanonicalJson(expected)

        val entries = mutableListOf<ComparisonDiff.DiffEntry>()
        compareElements("$", expectedJson, actualJson, JsonNull.INSTANCE, entries)
        return ComparisonDiff(entries)
    }

    private fun toCanonicalJson(resource: Resource): JsonElement =
        try {
            val json = fhirJsonParser.composeString(resource)
            val raw = JsonParser.parseString(json)
            normalize(raw, "$")
        } catch (exception: IOException) {
            throw IllegalStateException("Unable to serialize resource for comparison", exception)
        }

    private fun normalize(element: JsonElement?, path: String): JsonElement {
        if (element == null || element.isJsonNull) {
            return JsonNull.INSTANCE
        }

        if (element.isJsonObject) {
            return element.asJsonObject.entrySet()
                .asSequence()
                .filterNot { (key, _) -> shouldIgnore(key, path) }
                .map { (key, value) -> key to normalize(value, "$path.$key") }
                .sortedBy { (key, _) -> key }
                .fold(JsonObject()) { normalized, (key, value) ->
                    normalized.apply { add(key, value) }
                }
        }

        if (element.isJsonArray) {
            return element.asJsonArray
                .mapIndexed { index, item -> normalize(item, "$path[]$index") }
                .sortedBy(JsonElement::toString)
                .fold(JsonArray()) { normalizedArray, value ->
                    normalizedArray.apply { add(value) }
                }
        }

        return element.deepCopy()
    }

    private fun shouldIgnore(fieldName: String, path: String): Boolean =
        fieldName == "id" || (path.endsWith(".meta") && fieldName in setOf("versionId", "lastUpdated"))

    private fun compareElements(
        path: String,
        expected: JsonElement?,
        actual: JsonElement?,
        parentContext: JsonElement?,
        entries: MutableList<ComparisonDiff.DiffEntry>
    ) {
        val safeExpected = defaultIfNull(expected)
        val safeActual = defaultIfNull(actual)

        if (safeExpected::class.java != safeActual::class.java) {
            addDiff(entries, path, ComparisonDiff.Category.TYPE_MISMATCH, safeExpected, safeActual, parentContext)
            return
        }

        if (safeExpected.isJsonObject) {
            compareObjects(path, safeExpected.asJsonObject, safeActual.asJsonObject, entries)
            return
        }

        if (safeExpected.isJsonArray) {
            compareArrays(path, safeExpected.asJsonArray, safeActual.asJsonArray, parentContext, entries)
            return
        }

        if (safeExpected != safeActual) {
            addDiff(entries, path, ComparisonDiff.Category.VALUE_MISMATCH, safeExpected, safeActual, parentContext)
        }
    }

    private fun defaultIfNull(element: JsonElement?): JsonElement = element ?: JsonNull.INSTANCE

    private fun compareObjects(
        path: String,
        expectedObject: JsonObject,
        actualObject: JsonObject,
        entries: MutableList<ComparisonDiff.DiffEntry>
    ) {
        expectedObject.entrySet().forEach { (key, expectedValue) ->
            val childPath = "$path.$key"
            if (!actualObject.has(key)) {
                addDiff(
                    entries,
                    childPath,
                    ComparisonDiff.Category.MISSING_FIELD,
                    expectedValue,
                    JsonNull.INSTANCE,
                    expectedObject
                )
                return@forEach
            }
            compareElements(childPath, expectedValue, actualObject.get(key), expectedObject, entries)
        }

        actualObject.entrySet().forEach { (key, actualValue) ->
            if (!expectedObject.has(key)) {
                val childPath = "$path.$key"
                addDiff(
                    entries,
                    childPath,
                    ComparisonDiff.Category.UNEXPECTED_FIELD,
                    JsonNull.INSTANCE,
                    actualValue,
                    actualObject
                )
            }
        }
    }

    private fun compareArrays(
        path: String,
        expectedArray: JsonArray,
        actualArray: JsonArray,
        parentContext: JsonElement?,
        entries: MutableList<ComparisonDiff.DiffEntry>
    ) {
        if (expectedArray.size() != actualArray.size()) {
            addDiff(
                entries,
                path,
                ComparisonDiff.Category.ARRAY_LENGTH_MISMATCH,
                expectedArray,
                actualArray,
                parentContext
            )
        }

        val max = maxOf(expectedArray.size(), actualArray.size())
        for (index in 0 until max) {
            val expectedItem = if (index < expectedArray.size()) expectedArray[index] else JsonNull.INSTANCE
            val actualItem = if (index < actualArray.size()) actualArray[index] else JsonNull.INSTANCE
            compareElements("$path[$index]", expectedItem, actualItem, parentContext, entries)
        }
    }

    private fun addDiff(
        entries: MutableList<ComparisonDiff.DiffEntry>,
        path: String,
        category: ComparisonDiff.Category,
        expected: JsonElement?,
        actual: JsonElement?,
        parentContext: JsonElement?
    ) {
        entries += ComparisonDiff.DiffEntry(
            path = path,
            category = category,
            expected = expected?.toString(),
            actual = actual?.toString(),
            parentContext = parentContext?.toString()
        )
    }
}

