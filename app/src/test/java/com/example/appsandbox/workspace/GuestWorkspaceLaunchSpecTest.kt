package com.example.appsandbox.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class GuestWorkspaceLaunchSpecTest {
    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"

    @Test fun launchSpecUsesStableInternalComponentAndDocumentIdentity() {
        val spec = GuestWorkspaceLaunchPolicy.create(idA)
        assertEquals("com.example.appsandbox.GuestWorkspaceActivity", spec.componentClassName)
        assertEquals("appsandbox://workspace/$idA", spec.documentUri)
        assertEquals(GuestWorkspaceLaunchPolicy.FLAG_NEW_DOCUMENT, spec.flags)
        assertFalse(spec.flags and GuestWorkspaceLaunchPolicy.FLAG_MULTIPLE_TASK != 0)
    }

    @Test fun repeatedInstanceProducesSameDocumentWhileDifferentInstancesDoNot() {
        assertEquals(GuestWorkspaceLaunchPolicy.create(idA), GuestWorkspaceLaunchPolicy.create(idA))
        assertFalse(GuestWorkspaceLaunchPolicy.create(idA).documentUri == GuestWorkspaceLaunchPolicy.create(idB).documentUri)
    }

    @Test fun invalidMissingOrMismatchedIntentIdentityFailsClosed() {
        listOf("", "../escape", "not-a-uuid", idA + "0").forEach { id -> expectRejected { GuestWorkspaceLaunchPolicy.create(id) } }
        expectRejected { GuestWorkspaceLaunchPolicy.validate(null, null) }
        expectRejected { GuestWorkspaceLaunchPolicy.validate(idA, "appsandbox://workspace/$idB") }
        assertEquals(idA, GuestWorkspaceLaunchPolicy.validate(idA, "appsandbox://workspace/$idA").instanceId)
    }

    @Test fun recentsLabelSeparatesPackageAndInstance() {
        assertEquals("com.example.alpha / 11111111", GuestWorkspaceTaskPolicy.recentsLabel("com.example.alpha", idA))
        assertFalse(
            GuestWorkspaceTaskPolicy.recentsLabel("com.example.alpha", idA) ==
                GuestWorkspaceTaskPolicy.recentsLabel("com.example.alpha", idB)
        )
        assertEquals("Open", GuestWorkspaceTaskPolicy.actionLabel(false))
        assertEquals("Focus", GuestWorkspaceTaskPolicy.actionLabel(true))
    }

    private fun expectRejected(action: () -> Unit) {
        try { action(); fail("expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
}
