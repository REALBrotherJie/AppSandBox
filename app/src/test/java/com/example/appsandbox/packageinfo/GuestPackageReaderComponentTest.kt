package com.example.appsandbox.packageinfo

import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.example.appsandbox.model.resolver.GuestComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestPackageReaderComponentTest {
    @Test
    fun normalizesAllArchiveComponentArraysAndPermissions() {
        val info = PackageInfo().apply {
            packageName = "com.example.fixture"
            activities = arrayOf(
                ActivityInfo().apply {
                    name = ".VisibleActivity"
                    enabled = true
                    exported = true
                },
                ActivityInfo().apply {
                    name = "com.example.fixture.PrivateActivity"
                    enabled = true
                    exported = false
                }
            )
            services = arrayOf(
                ServiceInfo().apply {
                    name = "EnabledService"
                    enabled = true
                    exported = true
                    permission = "com.example.fixture.permission.TEST"
                },
                ServiceInfo().apply {
                    name = ".DisabledService"
                    enabled = false
                    exported = true
                }
            )
            receivers = arrayOf(
                ActivityInfo().apply {
                    name = ".Receiver"
                    enabled = true
                    exported = true
                }
            )
            providers = arrayOf(
                ProviderInfo().apply {
                    name = ".Provider"
                    enabled = true
                    exported = true
                    readPermission = "com.example.fixture.permission.READ"
                    writePermission = "com.example.fixture.permission.WRITE"
                }
            )
        }

        val components = GuestPackageReader.normalizeComponents(info, "revision-a")

        assertEquals(6, components.size)
        assertEquals(setOf(GuestComponentType.ACTIVITY, GuestComponentType.SERVICE, GuestComponentType.RECEIVER, GuestComponentType.PROVIDER), components.map { it.type }.toSet())
        assertTrue(components.single { it.className.endsWith(".VisibleActivity") }.exported)
        assertFalse(components.single { it.className.endsWith(".PrivateActivity") }.exported)
        assertFalse(components.single { it.className.endsWith(".DisabledService") }.enabled)
        assertEquals(
            listOf("com.example.fixture.permission.READ", "com.example.fixture.permission.WRITE"),
            components.single { it.className.endsWith(".Provider") }.declaredPermissions
        )
        assertEquals(
            listOf("com.example.fixture.permission.TEST"),
            components.single { it.className.endsWith(".EnabledService") }.declaredPermissions
        )
    }

    @Test
    fun duplicateSameTypeDeclarationIsRejected() {
        val info = PackageInfo().apply {
            packageName = "com.example.fixture"
            activities = arrayOf(
                ActivityInfo().apply { name = ".Same"; enabled = true; exported = true },
                ActivityInfo().apply { name = "com.example.fixture.Same"; enabled = true; exported = true }
            )
        }

        try {
            GuestPackageReader.normalizeComponents(info, "revision-a")
            throw AssertionError("expected duplicate declaration rejection")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Duplicate"))
        }
    }
}
