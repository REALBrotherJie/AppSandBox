package com.example.appsandbox.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GuestViewContractTest {
    private val valid = """
        schema=1
        state=guest_counter|counter
        action=guest_increment|counter.increment|counter
        action=guest_reset|counter.reset|counter
        action=guest_toggle|counter.toggle|counter
    """.trimIndent()

    @Test fun v1AndV2VersionsAreSupported() {
        GuestContractVersions.requireSupported(1)
        GuestContractVersions.requireSupported(2)
        expectRejected { GuestContractVersions.requireSupported(3) }
    }

    @Test fun missingContractResourceFailsClosed() {
        assertEquals(42, GuestContractResources.requirePresent(42, "layout"))
        expectRejected { GuestContractResources.requirePresent(0, "action specification") }
    }

    @Test fun validV2SpecParsesOnlyWhitelistedActions() {
        val (state, actions) = GuestActionSpecParser.parse(valid)
        assertEquals("guest_counter", state)
        assertEquals(listOf(GuestAction.INCREMENT, GuestAction.RESET, GuestAction.TOGGLE), actions.map { it.action })
        assertTrue(actions.all { it.stateKey == "counter" })
    }

    @Test fun unknownAndMaliciousActionsFailClosed() {
        listOf(
            "java.lang.Runtime.exec", "intent.start", "file.read", "https://example.invalid", "counter.increment;rm"
        ).forEach { action ->
            expectRejected { GuestActionSpecParser.parse("schema=1\nstate=s|counter\naction=b|$action|counter") }
        }
    }

    @Test fun malformedUnknownAndOversizedFieldsFailClosed() {
        listOf(
            "schema=2\nstate=s|counter\naction=b|counter.increment|counter",
            "schema=1\nunknown=value\nstate=s|counter\naction=b|counter.increment|counter",
            "schema=1\nstate=../escape|counter\naction=b|counter.increment|counter",
            "schema=1\nstate=s|counter\naction=b|counter.increment|path",
            "x".repeat(4097)
        ).forEach { expectRejected { GuestActionSpecParser.parse(it) } }
    }

    @Test fun duplicateControlActionAndStateBindingsFailClosed() {
        expectRejected { GuestActionSpecParser.parse(valid + "\naction=guest_increment|counter.reset|counter") }
        expectRejected { GuestActionSpecParser.parse(valid + "\naction=other|counter.increment|counter") }
        expectRejected { GuestActionSpecParser.parse(valid + "\nstate=other|counter") }
    }

    @Test fun missingAndWrongControlTypesPreventAllBinding() {
        val (state, actions) = GuestActionSpecParser.parse(valid)
        val contract = GuestViewContract(2, "layout", state, actions)
        val validControls = listOf(
            GuestResolvedControl("guest_counter", GuestControlType.TEXT),
            GuestResolvedControl("guest_increment", GuestControlType.BUTTON),
            GuestResolvedControl("guest_reset", GuestControlType.BUTTON),
            GuestResolvedControl("guest_toggle", GuestControlType.BUTTON)
        )
        GuestActionBindingValidator.validate(contract, validControls)
        expectRejected { GuestActionBindingValidator.validate(contract, validControls.dropLast(1)) }
        expectRejected {
            GuestActionBindingValidator.validate(
                contract,
                validControls.map { if (it.viewIdName == "guest_increment") it.copy(type = GuestControlType.TEXT) else it }
            )
        }
    }

    private fun expectRejected(action: () -> Unit) {
        try { action(); fail("expected rejection") }
        catch (_: IllegalArgumentException) {}
        catch (_: IllegalStateException) {}
    }
}
