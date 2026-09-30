package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
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
}
