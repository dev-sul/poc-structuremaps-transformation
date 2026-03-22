package de.gematik.test.fhir

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.Resource
import java.util.UUID

internal class SourceBundleBuilder {
    fun build(sources: List<Resource?>): Bundle {
        return Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            sources.filterNotNull().forEach { appendResource(this, it) }
        }
    }

    private fun appendResource(target: Bundle, source: Resource) {
        when {
            appendDocumentEntriesIfApplicable(target, source) -> Unit
            appendParameterEntriesIfApplicable(target, source) -> Unit
            else -> addAsEntry(target, source, null)
        }
    }

    private fun appendDocumentEntriesIfApplicable(target: Bundle, source: Resource): Boolean {
        if (source !is Bundle || source.type != Bundle.BundleType.DOCUMENT) {
            return false
        }

        source.entry
            .filter { it.hasResource() }
            .forEach { addAsEntry(target, it.resource, it.fullUrl) }
        return true
    }

    private fun appendParameterEntriesIfApplicable(target: Bundle, source: Resource): Boolean {
        if (source !is Parameters) {
            return false
        }

        source.parameter
            .flatMap { it.part }
            .filter { it.hasResource() }
            .forEach { addAsEntry(target, it.resource, null) }
        return true
    }

    private fun addAsEntry(target: Bundle, resource: Resource, fullUrl: String?) {
        val entry = target.addEntry()
        entry.resource = resource

        if (!fullUrl.isNullOrBlank()) {
            entry.fullUrl = fullUrl
            return
        }

        entry.fullUrl = if (resource.hasIdElement() && resource.idElement.hasIdPart()) {
            "urn:uuid:${resource.idElement.idPart}"
        } else {
            "urn:uuid:${UUID.randomUUID()}"
        }
    }
}

