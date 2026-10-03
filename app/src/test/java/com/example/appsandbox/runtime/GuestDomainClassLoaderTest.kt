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
            SystemClassSource.GUEST to "guest",
            SystemClassSource.HOST to "host"
        )
        val name = "android.support.v4.os.ResultReceiver"
        assertEquals("guest", resolve(name, definitions))
    }

    @Test fun guestDefinitionIsReachedWhenPlatformAndHostMiss() {
        listOf("javax.inject.Provider", "android.view.OdViewStub", "android.support.v4.content.FileProvider").forEach { name ->
            assertEquals(name, "guest", resolve(name, mapOf(SystemClassSource.GUEST to "guest")))
        }
    }

    @Test fun platformClassesStayWithPlatformEvenIfGuestShadowsThem() {
        val all = mapOf(SystemClassSource.PLATFORM to "platform", SystemClassSource.GUEST to "guest", SystemClassSource.HOST to "host")
        assertEquals("platform", resolve("android.app.Activity", all))
        assertEquals("platform", resolve("java.lang.String", all))
    }

    @Test fun javaNamespaceNeverConsultsGuestDex() {
        val visited = mutableListOf<SystemClassSource>()
        try {
            GuestDomainClassLoader.resolveInOrder("java.lang.Fake", GuestDomainClassLoader.systemLookupOrder("java.lang.Fake")) {
                visited += it
                throw ClassNotFoundException(it.name)
            }
            fail("expected ClassNotFoundException")
        } catch (expected: ClassNotFoundException) {
            assertEquals("java.lang.Fake", expected.message)
        }
        assertTrue(SystemClassSource.GUEST !in visited)
    }

    @Test fun guestLinkageFailureFallsBackToHostAndKeepsFirstCause() {
        val result = GuestDomainClassLoader.resolveInOrder("javax.x.Y", GuestDomainClassLoader.systemLookupOrder("javax.x.Y")) { source ->
            when (source) {
                SystemClassSource.PLATFORM -> throw ClassNotFoundException("platform")
                SystemClassSource.GUEST -> throw NoClassDefFoundError("guest link")
                SystemClassSource.HOST -> "host"
            }
        }
        assertEquals("host", result)
    }

    private fun resolve(name: String, definitions: Map<SystemClassSource, String>): String =
        GuestDomainClassLoader.resolveInOrder(name, GuestDomainClassLoader.systemLookupOrder(name)) { source ->
            definitions[source] ?: throw ClassNotFoundException("$source:$name")
        }
}
