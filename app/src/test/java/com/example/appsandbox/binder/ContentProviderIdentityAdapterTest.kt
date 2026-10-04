package com.example.appsandbox.binder

import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.os.IBinder
import android.os.IInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

class ContentProviderIdentityAdapterTest {
    @Test fun binderFacadeIdentityIsStableWhenRetainedByOneProviderWrapper() {
        val physical = fakeBinder("physical-a")
        val provider = Proxy.newProxyInstance(IInterface::class.java.classLoader, arrayOf(IInterface::class.java)) { _, method, _ ->
            if (method.name == "asBinder") physical else null
        } as IInterface
        val retained = AtomicReference<IBinder?>()
        val first = ContentProviderIdentityAdapter.stableBinderFacade(retained, physical) { provider }
        val second = ContentProviderIdentityAdapter.stableBinderFacade(retained, physical) { provider }

        assertSame(first, second)
        assertSame(provider, first.queryLocalInterface("android.content.IContentProvider"))
        assertTrue(first.isBinderAlive)
    }

    @Test fun differentProviderWrappersDoNotShareBinderFacades() {
        val physical = fakeBinder("physical-shared")
        val first = ContentProviderIdentityAdapter.createBinderFacade(physical) { Any() }
        val second = ContentProviderIdentityAdapter.createBinderFacade(physical) { Any() }

        assertNotSame(first, second)
    }

    @Test fun physicalHolderUsesHostUserWithoutMutatingGuestProviderIdentity() {
        val guest = ProviderInfo().apply {
            authority = "guest.authority"
            applicationInfo = ApplicationInfo().apply { uid = -1234567; packageName = "guest.pkg" }
        }
        val physical = ActivityManagerAdapter.physicalProviderHolderInfo(guest, 10123)

        assertEquals(10123, physical.applicationInfo.uid)
        assertEquals(-1234567, guest.applicationInfo.uid)
        assertNotSame(guest, physical)
        assertNotSame(guest.applicationInfo, physical.applicationInfo)
    }

    private fun fakeBinder(label: String): IBinder = Proxy.newProxyInstance(
        IBinder::class.java.classLoader,
        arrayOf(IBinder::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "isBinderAlive", "pingBinder" -> true
            "queryLocalInterface" -> null
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> label
            else -> null
        }
    } as IBinder
}
