package de.gematik.test.fhir

import java.util.EnumMap

class ComparisonDiff(entries: List<DiffEntry>) {
    val entries: List<DiffEntry> = entries.toList()

    enum class Category {
        TYPE_MISMATCH,
        VALUE_MISMATCH,
        MISSING_FIELD,
        UNEXPECTED_FIELD,
        ARRAY_LENGTH_MISMATCH
    }

    data class DiffEntry(
        val path: String,
        val category: Category,
        val expected: String?,
        val actual: String?,
        val parentContext: String?
    ) {
        fun path(): String = this.path
        fun category(): Category = this.category
        fun expected(): String? = this.expected
        fun actual(): String? = this.actual
        fun parentContext(): String? = this.parentContext
    }

    fun entries(): List<DiffEntry> = entries

    fun isEqual(): Boolean = entries.isEmpty()

    fun summary(): String {
        if (isEqual()) {
            return "No differences found."
        }

        val counts = EnumMap<Category, Int>(Category::class.java)
        entries.forEach { entry ->
            counts.merge(entry.category, 1, Int::plus)
        }

        return buildString {
            append("Differences found: ")
            append(entries.size)
            for (category in Category.entries) {
                val value = counts[category]
                if (value != null && value > 0) {
                    append(" | ")
                    append(category.name)
                    append('=')
                    append(value)
                }
            }

            appendLine()
            append("Examples:")
            entries
                .take(MAX_SUMMARY_EXAMPLES)
                .forEach { entry ->
                    appendLine()
                    append(" - ")
                    append(entry.path)
                    append(" [")
                    append(entry.category)
                    append("] expected=")
                    append(entry.expected)
                    append(" actual=")
                    append(entry.actual)
                }

            if (entries.size > MAX_SUMMARY_EXAMPLES) {
                appendLine()
                append(" - ... and ")
                append(entries.size - MAX_SUMMARY_EXAMPLES)
                append(" more")
            }
        }
    }

    private companion object {
        private const val MAX_SUMMARY_EXAMPLES = 5
    }
}

