package com.example.appsandbox.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestExternalStoragePolicyTest {
    private val i0 = GuestExternalStoragePolicy("com.guest", "com.host", "i0")
    private val i1 = GuestExternalStoragePolicy("com.guest", "com.host", "i1")

    @Test
    fun appSpecificDirsOfEachKindMapUnderHostInstanceRoot() {
        assertEquals("/storage/emulated/0/Android/data/com.host/virtual/i0/files",
            i0.map("/storage/emulated/0/Android/data/com.guest/files"))
        assertEquals("/storage/emulated/0/Android/obb/com.host/virtual/i0",
            i0.map("/storage/emulated/0/Android/obb/com.guest"))
        assertEquals("/storage/ABCD-1234/Android/media/com.host/virtual/i0/x/y",
            i0.map("/storage/ABCD-1234/Android/media/com.guest/x/y"))
    }

    @Test
    fun instancesOfOnePackageNeverShareAPhysicalDirectory() {
        val logical = "/storage/emulated/0/Android/data/com.guest/cache"
        assert(i0.map(logical) != i1.map(logical))
    }

    @Test
    fun otherPackagesAndPrefixLookalikesStayUnchanged() {
        listOf(
            "/storage/emulated/0/Android/data/com.guest.other/files",
            "/storage/emulated/0/Android/data/com.other/files",
            "/storage/emulated/0/DCIM/com.guest",
            "/storage/emulated/0/Android/data/com.host/files"
        ).forEach { assertEquals(it, i0.map(it)) }
    }

    @Test
    fun mappedPathIsAFixedPoint() {
        val physical = i0.map("/storage/emulated/0/Android/data/com.guest/files")
        assertEquals(physical, i0.map(physical))
    }
}
