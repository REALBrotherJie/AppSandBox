package com.example.appsandbox.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GuestViewSessionTest {
    @Test fun incrementResetToggleAndRestartPersist() = withRoots { a, _ ->
        val first = GuestViewSession(a)
        assertEquals(1, first.execute(GuestAction.INCREMENT))
        assertEquals(0, first.execute(GuestAction.TOGGLE))
        assertEquals(1, first.execute(GuestAction.TOGGLE))
        assertEquals(0, first.execute(GuestAction.RESET))
        assertEquals(0, GuestViewSession(a).counter())
    }

    @Test fun separateInstancesAndRevisionsCannotReadEachOther() = withRoots { a, b ->
        GuestViewSession(a).execute(GuestAction.INCREMENT)
        GuestViewSession(a).execute(GuestAction.INCREMENT)

        assertEquals(2, GuestViewSession(a).counter())
        assertEquals(0, GuestViewSession(b).counter())
    }

    @Test fun deletingOneInstanceDoesNotChangeTheOther() = withRoots { a, b ->
        GuestViewSession(a).execute(GuestAction.INCREMENT)
        GuestViewSession(b).execute(GuestAction.TOGGLE)
        a.deleteRecursively()

        assertFalse(a.exists())
        assertEquals(1, GuestViewSession(b).counter())
    }

    private fun withRoots(block: (File, File) -> Unit) {
        val base = createTempDirectory("guest-view-session").toFile()
        try { block(File(base, "instance-a"), File(base, "instance-b")) }
        finally { base.deleteRecursively() }
    }
}
