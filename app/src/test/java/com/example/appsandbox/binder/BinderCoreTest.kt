package com.example.appsandbox.binder

import com.example.appsandbox.identity.RuntimeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderCoreTest {
    private val first = RuntimeIdentity("host.pkg", 10123, "guest.pkg", "guest.pkg:0", "0", 1)

    interface Probe { fun call(value: String): String }

    @Test fun registryRejectsDuplicateServicesAndInstallsOnce() {
        var installs = 0
        val adapter = object : BinderServiceAdapter {
            override val serviceName = "probe"
            override val interfaceName = Probe::class.java.name
            override fun install() = AdapterInstallResult(serviceName, true).also { installs++ }
        }
        val registry = BinderAdapterRegistry(listOf(adapter))
        assertEquals(setOf("probe"), registry.serviceNames())
        assertTrue(registry.installAll(31).single().installed)
        assertTrue(registry.installAll(36).single().alreadyInstalled)
        assertEquals(1, installs)
        runCatching { registry.register(adapter) }.onSuccess { error("duplicate accepted") }
    }

    @Test fun methodPolicySupportsLookupResultAdaptationAndUnknownModes() {
        val method = Probe::class.java.getMethod("call", String::class.java)
        val call = BinderCallContext("probe", Probe::class.java.name, method, first, arrayOf("logical"))
        val registry = MethodPolicyRegistry().register("call") { _, physical ->
            BinderCallResult(physical(arrayOf("physical")).toString().uppercase(), BinderRoute.PHYSICAL, IdentityDecision.USE_PHYSICAL)
        }
        assertEquals("PHYSICAL", registry.invoke(call) { it[0] }.value)
        val unknown = BinderCallContext("probe", Probe::class.java.name,
            Any::class.java.getMethod("toString"), first, emptyArray())
        assertSame(first, MethodPolicyRegistry().invoke(unknown) { first }.value)
        runCatching { MethodPolicyRegistry(UnknownMethodBehavior.EXPLICIT_BLOCK).invoke(unknown) { null } }
            .onSuccess { error("unknown method was not blocked") }
    }

    @Test fun identityPolicyRewritesOnlyDeclaredSemanticPositionsAndSeparatesInstances() {
        val second = first.copy(virtualUid = "guest.pkg:1", instanceId = "1", processSlot = 2)
        assertNotEquals(first.virtualUidNumber, second.virtualUidNumber)
        val args = arrayOf<Any?>("guest.pkg", "guest.pkg", first.virtualUidNumber, first.virtualUidNumber)
        val rewritten = IdentityPolicy(first).rewritePackageUidAt(args, setOf(1), setOf(3))
        assertEquals("guest.pkg", rewritten[0])
        assertEquals("host.pkg", rewritten[1])
        assertEquals(first.virtualUidNumber, rewritten[2])
        assertEquals(10123, rewritten[3])
    }

    @Test fun proxyDefinesObjectMethodsAndPreservesUnderlyingException() {
        val original = object : Probe {
            override fun call(value: String): String = if (value == "boom") throw IllegalStateException("physical") else value
        }
        val proxy = ProxySupport.create(original, Probe::class.java, "probe", first, MethodPolicyRegistry()) as Probe
        assertTrue(proxy.toString().startsWith("VirtualBinderProxy(probe"))
        assertTrue(proxy == proxy)
        assertFalse(proxy == original)
        assertEquals(System.identityHashCode(proxy), proxy.hashCode())
        val error = runCatching { proxy.call("boom") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("physical", error?.message)
    }

    @Test fun notificationIdentityRewritesOnlyExactGuestPackageArguments() {
        val args = arrayOf<Any?>("guest.pkg", "guest.pkg.channel", "tag", 7)
        val rewritten = NotificationIdentityPolicy(first).physicalArgs(args)

        assertEquals("host.pkg", rewritten[0])
        assertEquals("guest.pkg.channel", rewritten[1])
        assertEquals("tag", rewritten[2])
        assertEquals(7, rewritten[3])
        assertEquals("guest.pkg", args[0])
    }
}
