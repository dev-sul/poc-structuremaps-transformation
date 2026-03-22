package de.gematik.test.fhir

import org.hl7.fhir.utilities.npm.NpmPackage
import org.hl7.fhir.validation.ValidationEngine

object FhirEngineFactory {
    const val DEFAULT_CORE_PACKAGE: String = "hl7.fhir.r4.core#4.0.1"

    @JvmStatic
    fun createDefault(): ValidationEngine =
        createWithIgs(DEFAULT_CORE_PACKAGE, "/de.gematik.erp.t-prescription-1.1.0-snapshots.tgz")

    @JvmStatic
    fun createWithIgs(corePackage: String, igResourcePath: String): ValidationEngine {
        val sanitizedCorePackage = requireNonBlank(corePackage, "corePackage")
        val sanitizedIgResourcePath = requireNonBlank(igResourcePath, "igResourcePath")

        return try {
            ValidationEngine.ValidationEngineBuilder().fromSource(sanitizedCorePackage).apply {
                FhirEngineFactory::class.java.getResourceAsStream(sanitizedIgResourcePath).use { stream ->
                    requireNotNull(stream) { "IG package resource not found: $sanitizedIgResourcePath" }
                    igLoader.loadPackage(NpmPackage.fromPackage(stream), true)
                }
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to initialize ValidationEngine", exception)
        }
    }

    private fun requireNonBlank(value: String, name: String): String =
        value.trim().also { sanitized ->
            require(sanitized.isNotEmpty()) { "$name must not be blank" }
        }
}

