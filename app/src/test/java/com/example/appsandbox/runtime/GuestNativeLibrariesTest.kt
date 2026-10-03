package com.example.appsandbox.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GuestNativeLibrariesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun splitOnlyNativeLibrariesLandInSingleDirectory() {
        val base = apk("base.apk", "classes.dex" to "dex")
        val abiSplit = apk("split_config.arm64_v8a.apk", "lib/arm64-v8a/libflutter.so" to "engine", "lib/arm64-v8a/libapp.so" to "aot")
        val result = requireNotNull(materializeGuestNativeLibraries(listOf(base.path, abiSplit.path), listOf("arm64-v8a"), temp.newFolder("out")))

        assertEquals("arm64-v8a", result.abi)
        assertFalse(result.directory.path.contains(File.pathSeparator))
        assertEquals("aot", File(result.directory, "libapp.so").readText())
        assertEquals("engine", File(result.directory, "libflutter.so").readText())
    }

    @Test fun baseAndSplitLibrariesMergeAndBaseWinsConflicts() {
        val base = apk("base.apk", "lib/arm64-v8a/libcore.so" to "base-core", "lib/arm64-v8a/libdup.so" to "base-dup")
        val split = apk("split1.apk", "lib/arm64-v8a/libextra.so" to "extra", "lib/arm64-v8a/libdup.so" to "split-dup")
        val result = requireNotNull(materializeGuestNativeLibraries(listOf(base.path, split.path), listOf("arm64-v8a"), temp.newFolder("out")))

        assertEquals(setOf("libcore.so", "libdup.so", "libextra.so"), result.directory.list()!!.toSet())
        assertEquals("base-dup", File(result.directory, "libdup.so").readText())
        assertEquals(1, result.conflicts.size)
    }

    @Test fun abiFollowsCandidateOrderAcrossSplits() {
        val base = apk("base.apk", "lib/x86_64/libx.so" to "x86")
        val split = apk("split_arm.apk", "lib/arm64-v8a/libx.so" to "arm")
        val result = requireNotNull(materializeGuestNativeLibraries(listOf(base.path, split.path), listOf("arm64-v8a", "x86_64"), temp.newFolder("out")))

        assertEquals("arm64-v8a", result.abi)
        assertEquals("arm", File(result.directory, "libx.so").readText())
        assertTrue(result.directory.path.endsWith("arm64-v8a"))
    }

    @Test fun packageWithoutNativeCodeReturnsNull() {
        val base = apk("base.apk", "classes.dex" to "dex")
        assertNull(materializeGuestNativeLibraries(listOf(base.path), listOf("arm64-v8a"), temp.newFolder("out")))
    }

    private fun apk(name: String, vararg entries: Pair<String, String>): File =
        temp.newFile(name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (path, content) ->
                    zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
                }
            }
        }
}
