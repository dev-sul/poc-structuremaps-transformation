package de.gematik.test.fhir

import org.hl7.fhir.r4.formats.JsonParser
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r5.elementmodel.Element
import org.hl7.fhir.r5.formats.JsonCreatorGson
import org.hl7.fhir.r5.model.CodeableConcept
import org.hl7.fhir.r5.model.Coding
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.utils.validation.constants.CheckDisplayOption
import org.hl7.fhir.utilities.ByteProvider
import org.hl7.fhir.utilities.validation.ValidationMessage
import org.hl7.fhir.validation.ValidationEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class StructureMapTransformer(
    private val validationEngine: ValidationEngine,
    structureMapUrl: String
) {
    private val structureMapUrl: String = requireStructureMapUrl(structureMapUrl)
    private val bundleBuilder = SourceBundleBuilder()
    private val r4JsonParser = JsonParser()

    fun transform(sources: List<Resource?>): Resource {
        return try {
            val sourceBundle = bundleBuilder.build(sources)
            val inputJson = r4JsonParser.composeBytes(sourceBundle)

            val transformed = validationEngine.transform(
                ByteProvider.forBytes(inputJson),
                org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON,
                structureMapUrl
            )

            parseR4Resource(transformed)
        } catch (exception: Exception) {
            throw IllegalStateException("Transformation failed", exception)
        }
    }

    fun validate(resource: Resource): OperationOutcome {
        return try {
            val json = r4JsonParser.composeBytes(resource)
            val validator = validationEngine.getValidator(org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON)
            validator.setNoTerminologyChecks(true)
            validator.setValidateValueSetCodesOnTxServer(false)
            validator.checkDisplay = CheckDisplayOption.Ignore
            validator.setUnknownCodeSystemsCauseErrors(false)

            val messages = mutableListOf<ValidationMessage>()
            ByteArrayInputStream(json).use { input ->
                validator.validate(null, messages, input, org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON)
            }
            toOutcome(messages)
        } catch (exception: Exception) {
            throw IllegalStateException("Validation failed", exception)
        }
    }

    private fun toOutcome(messages: List<ValidationMessage>): OperationOutcome {
        val outcome = OperationOutcome()
        for (message in messages) {
            val issue = outcome.addIssue()
            issue.severity = mapSeverity(message.level)
            issue.code = mapIssueType(message.type)
            issue.diagnostics = message.summary()
            message.messageId
                ?.takeIf(String::isNotBlank)
                ?.let { id ->
                    issue.details = CodeableConcept().addCoding(
                        Coding().setSystem(FHIR_VALIDATOR_MESSAGE_SYSTEM).setCode(id)
                    )
                }
            message.location?.let(issue::addLocation)
            message.locationLink?.takeIf(String::isNotBlank)?.let(issue::addLocation)
        }
        return outcome
    }

    private fun requireStructureMapUrl(structureMapUrl: String): String {
        val sanitizedStructureMapUrl = structureMapUrl.trim()
        require(sanitizedStructureMapUrl.isNotEmpty()) { "structureMapUrl must not be blank" }
        return sanitizedStructureMapUrl
    }

    private fun mapSeverity(severity: ValidationMessage.IssueSeverity?): OperationOutcome.IssueSeverity =
        when (severity) {
            ValidationMessage.IssueSeverity.FATAL -> OperationOutcome.IssueSeverity.FATAL
            ValidationMessage.IssueSeverity.ERROR -> OperationOutcome.IssueSeverity.ERROR
            ValidationMessage.IssueSeverity.WARNING -> OperationOutcome.IssueSeverity.WARNING
            else -> OperationOutcome.IssueSeverity.INFORMATION
        }

    private fun mapIssueType(issueType: ValidationMessage.IssueType?): OperationOutcome.IssueType =
        when (issueType) {
            ValidationMessage.IssueType.INVALID -> OperationOutcome.IssueType.INVALID
            ValidationMessage.IssueType.STRUCTURE -> OperationOutcome.IssueType.STRUCTURE
            ValidationMessage.IssueType.REQUIRED -> OperationOutcome.IssueType.REQUIRED
            ValidationMessage.IssueType.VALUE -> OperationOutcome.IssueType.VALUE
            ValidationMessage.IssueType.INVARIANT -> OperationOutcome.IssueType.INVARIANT
            ValidationMessage.IssueType.SECURITY -> OperationOutcome.IssueType.SECURITY
            ValidationMessage.IssueType.LOGIN -> OperationOutcome.IssueType.LOGIN
            ValidationMessage.IssueType.UNKNOWN -> OperationOutcome.IssueType.UNKNOWN
            ValidationMessage.IssueType.EXPIRED -> OperationOutcome.IssueType.EXPIRED
            ValidationMessage.IssueType.FORBIDDEN -> OperationOutcome.IssueType.FORBIDDEN
            ValidationMessage.IssueType.SUPPRESSED -> OperationOutcome.IssueType.SUPPRESSED
            ValidationMessage.IssueType.PROCESSING -> OperationOutcome.IssueType.PROCESSING
            ValidationMessage.IssueType.NOTSUPPORTED -> OperationOutcome.IssueType.NOTSUPPORTED
            ValidationMessage.IssueType.DUPLICATE -> OperationOutcome.IssueType.DUPLICATE
            ValidationMessage.IssueType.MULTIPLEMATCHES -> OperationOutcome.IssueType.MULTIPLEMATCHES
            ValidationMessage.IssueType.NOTFOUND -> OperationOutcome.IssueType.NOTFOUND
            ValidationMessage.IssueType.DELETED -> OperationOutcome.IssueType.DELETED
            ValidationMessage.IssueType.TOOLONG -> OperationOutcome.IssueType.TOOLONG
            ValidationMessage.IssueType.CODEINVALID -> OperationOutcome.IssueType.CODEINVALID
            ValidationMessage.IssueType.EXTENSION -> OperationOutcome.IssueType.EXTENSION
            ValidationMessage.IssueType.TOOCOSTLY -> OperationOutcome.IssueType.TOOCOSTLY
            ValidationMessage.IssueType.BUSINESSRULE -> OperationOutcome.IssueType.BUSINESSRULE
            ValidationMessage.IssueType.CONFLICT -> OperationOutcome.IssueType.CONFLICT
            ValidationMessage.IssueType.INCOMPLETE -> OperationOutcome.IssueType.INCOMPLETE
            ValidationMessage.IssueType.TRANSIENT -> OperationOutcome.IssueType.TRANSIENT
            ValidationMessage.IssueType.LOCKERROR -> OperationOutcome.IssueType.LOCKERROR
            ValidationMessage.IssueType.NOSTORE -> OperationOutcome.IssueType.NOSTORE
            ValidationMessage.IssueType.EXCEPTION -> OperationOutcome.IssueType.EXCEPTION
            ValidationMessage.IssueType.TIMEOUT -> OperationOutcome.IssueType.TIMEOUT
            ValidationMessage.IssueType.THROTTLED -> OperationOutcome.IssueType.THROTTLED
            ValidationMessage.IssueType.INFORMATIONAL -> OperationOutcome.IssueType.INFORMATIONAL
            else -> OperationOutcome.IssueType.INVALID
        }

    companion object {
        // Custom coding system used to carry the HAPI FHIR validator message identifier
        // (ValidationMessage.messageId) in the OperationOutcome issue details. Consumers
        // can use these codes to look up the exact validation rule that was violated.
        private const val FHIR_VALIDATOR_MESSAGE_SYSTEM =
            "https://hapi.fhir.org/validation-message"
    }

    private fun parseR4Resource(transformed: Element): Resource {
        try {
            ByteArrayOutputStream().use { output ->
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    val creator = JsonCreatorGson(writer)
                    org.hl7.fhir.r5.elementmodel.JsonParser(validationEngine.context).compose(transformed, creator)
                    creator.finish()
                    writer.flush()
                }
                return r4JsonParser.parse(output.toByteArray())
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to parse transformed resource as R4", exception)
        }
    }
}

