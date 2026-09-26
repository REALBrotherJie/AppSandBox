package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GuestRevisionPolicyTest {
    private fun revision(id: String, sha: String) = GuestPackageRecord(id, "com.example.guest", "1", 1, "/guests/$id/base.apk", "Guest", 1, ComponentSummary(0, 0, 0, 0), id, sha, 10, 2)
    private fun instance(revision: GuestPackageRecord) = GuestInstanceRecord("11111111-1111-4111-8111-111111111111", revision.revisionId, revision.packageName, revision.apkPath, revision.sha256!!, "/instances/11111111-1111-4111-8111-111111111111", 1, 1)

    @Test fun samePackageRevisionsRemainDistinct() {
        val a = revision("revision-a", "a".repeat(64)); val b = revision("revision-b", "b".repeat(64))
        assertEquals(2, listOf(a, b).map { Triple(it.revisionId, it.apkPath, it.sha256) }.toSet().size)
    }
    @Test fun referencedRevisionDeletionIsRejected() {
        val a = revision("revision-a", "a".repeat(64))
        try { GuestRevisionPolicy.remove(listOf(a), listOf(instance(a)), a.revisionId); fail("expected reference protection") } catch (_: IllegalStateException) {}
    }
    @Test fun deletionSucceedsAfterLastInstanceIsRemovedAndPreservesOtherRevision() {
        val a = revision("revision-a", "a".repeat(64)); val b = revision("revision-b", "b".repeat(64))
        assertEquals(listOf(b), GuestRevisionPolicy.remove(listOf(a, b), emptyList(), a.revisionId))
    }
    @Test fun oldInstanceIdentityDoesNotFollowNewRevision() {
        val a = revision("revision-a", "a".repeat(64)); val b = revision("revision-b", "b".repeat(64)); val old = instance(a)
        assertEquals(a.revisionId, old.guestRevisionId); assertEquals(a.apkPath, old.guestApkPath); assertEquals(a.sha256, old.guestSha256)
        assert(old.guestRevisionId != b.revisionId)
    }
}
