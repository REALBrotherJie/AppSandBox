package com.example.appsandbox.storage

import com.example.appsandbox.model.ComponentSummary
import com.example.appsandbox.model.GuestInstanceRecord
import com.example.appsandbox.model.GuestPackageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GuestInstanceBindingTest {
    private val apk = File("build/test-a.apk").canonicalPath
    private val revision = GuestPackageRecord("g", "pkg.a", "1", 1, apk, "A", 1, ComponentSummary(0,0,0,0), "rev-a", "a".repeat(64), 1, 2)
    private val instance = GuestInstanceRecord("11111111-1111-4111-8111-111111111111", "rev-a", "pkg.a", apk, "a".repeat(64), File("build/i").canonicalPath, 1, 1)
    @Test fun exactIdentityAccepted() = assertNull(GuestInstanceBinding.validate(instance, revision))
    @Test fun packageMismatchRejected() = assertEquals("package mismatch", GuestInstanceBinding.validate(instance.copy(guestPackageName="pkg.b"), revision))
    @Test fun revisionMismatchRejected() = assertEquals("revision mismatch", GuestInstanceBinding.validate(instance.copy(guestRevisionId="rev-b"), revision))
    @Test fun pathMismatchRejected() = assertEquals("APK path mismatch", GuestInstanceBinding.validate(instance.copy(guestApkPath=File("build/b.apk").canonicalPath), revision))
    @Test fun shaMismatchRejected() = assertEquals("SHA mismatch", GuestInstanceBinding.validate(instance.copy(guestSha256="b".repeat(64)), revision))
}
