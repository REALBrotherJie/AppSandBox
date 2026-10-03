package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GuestDomainClassLoaderTest {
    @Test fun separatesSystemRuntimeAndGuestNamespaces() {
        assertEquals(ClassLoadingDomain.SYSTEM, GuestDomainClassLoader.domainOf("android.app.Activity"))
        assertEquals(ClassLoadingDomain.SYSTEM, GuestDomainClassLoader.domainOf("java.lang.String"))
        assertEquals(ClassLoadingDomain.RUNTIME_BRIDGE, GuestDomainClassLoader.domainOf("com.example.appsandbox.identity.RuntimeIdentity"))
        assertEquals(ClassLoadingDomain.GUEST, GuestDomainClassLoader.domainOf("androidx.activity.ComponentActivity"))
        assertEquals(ClassLoadingDomain.GUEST, GuestDomainClassLoader.domainOf("kotlin.collections.CollectionsKt"))
        assertEquals(ClassLoadingDomain.GUEST, GuestDomainClassLoader.domainOf("com.example.appsandbox.fake.Attack"))
    }

    @Test fun guestPackagedSystemNamespaceClassBeatsHostCopy() {
        // Host APK ships android.support.v4.os.ResultReceiver via androidx.core; a guest that bundles
        // its own support library must get its own definition, not the host's.
        val definitions = mapOf(
            ClassSource.GUEST to "guest",
            ClassSource.HOST to "host"
        )
        val name = "android.support.v4.os.ResultReceiver"
        assertEquals("guest", resolve(name, definitions))
    }

    @Test fun guestDefinitionIsReachedWhenPlatformAndHostMiss() {
        listOf("javax.inject.Provider", "android.view.OdViewStub", "android.support.v4.content.FileProvider").forEach { name ->
            assertEquals(name, "guest", resolve(name, mapOf(ClassSource.GUEST to "guest")))
        }
    }

    @Test fun platformClassesStayWithPlatformEvenIfGuestShadowsThem() {
        val all = mapOf(ClassSource.PLATFORM to "platform", ClassSource.GUEST to "guest", ClassSource.HOST to "host")
        assertEquals("platform", resolve("android.app.Activity", all))
        assertEquals("platform", resolve("java.lang.String", all))
    }

    @Test fun javaNamespaceAppClassResolvesFromGuestOnlyAfterPlatformMiss() {
        // Douyin Lite defines java.com.ss.android.ugc.aweme.mediachoose.helper.LivePhotoExportModel.
        assertEquals("guest", resolve("java.com.ss.android.Model", mapOf(ClassSource.GUEST to "guest", ClassSource.HOST to "host")))
        assertEquals("platform", resolve("java.lang.String", mapOf(ClassSource.PLATFORM to "platform", ClassSource.GUEST to "guest")))
    }

    @Test fun guestLinkageFailureFallsBackToHostAndKeepsFirstCause() {
        val result = GuestDomainClassLoader.resolveInOrder("javax.x.Y", order("javax.x.Y")) { source, _ ->
            when (source) {
                ClassSource.PLATFORM, ClassSource.SHARED_LIBRARY -> throw ClassNotFoundException(source.name)
                ClassSource.GUEST -> throw NoClassDefFoundError("guest link")
                ClassSource.HOST -> "host"
            }
        }
        assertEquals("host", result)
    }

    @Test fun sharedLibraryBeatsGuestAndHostForGuestDomainNames() {
        // org.apache.http.legacy: the declared uses-library supplies the class even if the Guest
        // bundles an older copy, exactly as the platform app loader does.
        val all = mapOf(ClassSource.SHARED_LIBRARY to "shared", ClassSource.GUEST to "guest", ClassSource.HOST to "host")
        assertEquals("shared", resolve("org.apache.http.conn.util.InetAddressUtils", all, shared = true))
        assertEquals("guest", resolve("com.kugou.Statistic", mapOf(ClassSource.GUEST to "guest", ClassSource.HOST to "host"), shared = true))
    }

    @Test fun platformStillBeatsSharedLibraryForSystemNames() {
        val all = mapOf(ClassSource.PLATFORM to "platform", ClassSource.SHARED_LIBRARY to "shared", ClassSource.GUEST to "guest")
        assertEquals("platform", resolve("android.test.AndroidTestCase", all, shared = true))
        assertEquals("shared", resolve("android.test.AndroidTestCase", all - ClassSource.PLATFORM, shared = true))
    }

    @Test fun lookupOrderWithoutSharedLibrariesKeepsGuestFirstForGuestDomain() {
        assertEquals(listOf(ClassSource.GUEST, ClassSource.HOST),
            GuestDomainClassLoader.lookupOrder(ClassLoadingDomain.GUEST, hasSharedLibraries = false))
        assertEquals(listOf(ClassSource.PLATFORM, ClassSource.SHARED_LIBRARY, ClassSource.GUEST, ClassSource.HOST),
            GuestDomainClassLoader.lookupOrder(ClassLoadingDomain.SYSTEM, hasSharedLibraries = true))
    }

    private fun order(name: String, shared: Boolean = false) =
        GuestDomainClassLoader.lookupOrder(GuestDomainClassLoader.domainOf(name), shared)

    private fun resolve(name: String, definitions: Map<ClassSource, String>, shared: Boolean = false): String =
        GuestDomainClassLoader.resolveInOrder(name, order(name, shared)) { source, _ ->
            definitions[source] ?: throw ClassNotFoundException("$source:$name")
        }
}
