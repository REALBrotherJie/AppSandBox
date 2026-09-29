package com.example.appsandbox.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GuestActivityLaunchPolicyTest {
    private val launchId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val instanceId = "11111111-1111-4111-8111-111111111111"
    private val revisionId = "22222222-2222-4222-8222-222222222222"
    private val packageName = "com.example.guest"
    private val componentName = "com.example.guest.GuestMainActivity"

    @Test
    fun launchSpecBindsInstanceRevisionPackageComponentAndUri() {
        val spec = GuestActivityLaunchPolicy.create(
            launchId, instanceId, revisionId, packageName, componentName
        )
        assertEquals("com.example.guest.GuestMainActivity", spec.componentName)
        assertEquals("appsandbox://activity/$instanceId/$launchId", spec.documentUri)
        assertTrue(GuestActivityLaunchPolicy.resultBelongsTo(launchId, launchId))
        assertFalse(GuestActivityLaunchPolicy.resultBelongsTo(launchId, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
        assertFalse(GuestActivityLaunchPolicy.resultBelongsTo(launchId, null))
    }

    @Test
    fun malformedIdsAndCrossInstanceUriAreRejected() {
        expectReason(GuestActivityLaunchReason.INVALID_ID) {
            GuestActivityLaunchPolicy.create("not-a-uuid", instanceId, revisionId, packageName, componentName)
        }
        expectReason(GuestActivityLaunchReason.INVALID_PACKAGE) {
            GuestActivityLaunchPolicy.create(launchId, instanceId, revisionId, "bad package", componentName)
        }
        expectReason(GuestActivityLaunchReason.INVALID_COMPONENT) {
            GuestActivityLaunchPolicy.create(launchId, instanceId, revisionId, packageName, "other.package.Activity")
        }
        expectReason(GuestActivityLaunchReason.URI_MISMATCH) {
            GuestActivityLaunchPolicy.validate(
                launchId, instanceId, revisionId, packageName, componentName,
                "appsandbox://activity/$instanceId/invalid"
            )
        }
    }

    private fun expectReason(expected: GuestActivityLaunchReason, action: () -> Unit) {
        try {
            action()
            fail("expected launch policy rejection")
        } catch (error: GuestActivityLaunchException) {
            assertEquals(expected, error.reason)
        }
    }
}
