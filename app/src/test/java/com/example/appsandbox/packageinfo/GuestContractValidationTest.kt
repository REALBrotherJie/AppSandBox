package com.example.appsandbox.packageinfo

import com.example.appsandbox.packageinfo.validation.GuestContractRejection
import com.example.appsandbox.packageinfo.validation.GuestContractValidation
import com.example.appsandbox.packageinfo.validation.GuestContractValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GuestContractValidationTest {
    private val valid = """
        schema=1
        state=guest_counter|counter
        action=guest_increment|counter.increment|counter
        action=guest_reset|counter.reset|counter
        action=guest_toggle|counter.toggle|counter
    """.trimIndent()

    @Test
    fun validSpecPreservesContractParserOutput() {
        val (state, actions) = GuestContractValidation.parseActionSpec(valid)
        assertEquals("guest_counter", state)
        assertEquals(3, actions.size)
    }

    @Test
    fun malformedMetadataAndResourcesHaveStableReasons() {
        assertReason(GuestContractRejection.UNKNOWN_VERSION) {
            GuestContractValidation.requireSupportedVersion(3)
        }
        assertReason(GuestContractRejection.MISSING_LAYOUT) {
            GuestContractValidation.requireMetadata(null, GuestContractRejection.MISSING_LAYOUT)
        }
        assertReason(GuestContractRejection.MISSING_ACTION_RESOURCE) {
            GuestContractValidation.requireResource(0, GuestContractRejection.MISSING_ACTION_RESOURCE)
        }
    }

    @Test
    fun negativeActionMatrixHasStableReasons() {
        val cases = mapOf(
            "unknown=field\n$valid" to GuestContractRejection.UNKNOWN_FIELD,
            valid.replace("counter.toggle", "counter.unknown") to GuestContractRejection.UNKNOWN_ACTION,
            valid + "\naction=guest_increment|counter.reset|counter" to GuestContractRejection.DUPLICATE_BINDING,
            valid + "\naction=guest_other|counter.increment|counter" to GuestContractRejection.DUPLICATE_ACTION,
            valid.replace("guest_increment", "GuestIncrement") to GuestContractRejection.INVALID_ID,
            valid.replace("state=guest_counter|counter", "state=guest_counter|other") to GuestContractRejection.INVALID_STATE_KEY
        )
        cases.forEach { (text, reason) -> assertReason(reason) { GuestContractValidation.parseActionSpec(text) } }
    }

    private fun assertReason(expected: GuestContractRejection, action: () -> Unit) {
        try {
            action()
            fail("expected ${expected.code}")
        } catch (error: GuestContractValidationException) {
            assertEquals(expected, error.rejection)
            assertEquals("Unsupported Guest: ${expected.code}", error.message)
        }
    }
}
