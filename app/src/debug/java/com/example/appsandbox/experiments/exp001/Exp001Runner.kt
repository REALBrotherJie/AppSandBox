package com.example.appsandbox.experiments.exp001

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import dalvik.system.DexClassLoader
import com.example.appsandbox.model.GuestPackageRecord
import com.example.appsandbox.storage.ArtifactState
import com.example.appsandbox.storage.GuestArtifactVerifier
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Exp001Runner {
    private const val TAG = "AppSandbox.Exp001"
    private const val guestClassName = "com.example.appsandbox.testguest.runtime.GuestProbe"

    @JvmStatic
    fun run(activity: Activity, record: GuestPackageRecord): String {
        val apk = File(record.apkPath)
        val verification = GuestArtifactVerifier.verify(record)
        check(verification.state == ArtifactState.VALID) {
            "${verification.state}: ${verification.message ?: "artifact verification failed"}"
        }
        val hostLoader = Exp001Runner::class.java.classLoader
        val lines = mutableListOf<String>()
        fun line(value: String) {
            Log.i(TAG, value)
            lines += value
        }

        line("guestId=${record.internalGuestId}")
        line("apkPath=${apk.absolutePath}")
        line("exists=${apk.exists()} readable=${apk.canRead()} size=${apk.length()}")
        line("sha256=${sha256(apk)}")
        line("packageName=${record.packageName}")
        line("guestInstalled=${isInstalled(activity, record.packageName)}")
        line("hostLoader=${hostLoader.javaClass.name}")
        line("hostLoaderToString=$hostLoader")
        line("hostParent=${hostLoader?.parent?.javaClass?.name ?: "null"}")
        line("hostParentToString=${hostLoader?.parent ?: "null"}")

        val hostNegative = try {
            hostLoader?.loadClass(guestClassName)
            "INVALID: FOUND"
        } catch (_: ClassNotFoundException) {
            "NOT FOUND"
        }
        line("hostClassLoader->$guestClassName=$hostNegative")
        check(hostNegative == "NOT FOUND") { "Host class loader contains GuestProbe" }

        val loaderA = createLoader(activity, apk)
        val resultA = invokeProbe(loaderA, lines, "A")
        val loaderB = createLoader(activity, apk)
        val resultB = invokeProbe(loaderB, lines, "B")
        line("classA==classB=${resultA.clazz == resultB.clazz}")
        line("classA.classLoader=${resultA.clazz.classLoader?.javaClass?.name}")
        line("classB.classLoader=${resultB.clazz.classLoader?.javaClass?.name}")
        line("classA.classLoader==classB.classLoader=${resultA.clazz.classLoader === resultB.clazz.classLoader}")
        line("classA.isAssignableFrom(classB)=${resultA.clazz.isAssignableFrom(resultB.clazz)}")
        line("classB.isAssignableFrom(classA)=${resultB.clazz.isAssignableFrom(resultA.clazz)}")
        line("classLoaderA==classLoaderB=${loaderA === loaderB}")
        line("java.lang.String.classLoader=${loaderA.loadClass("java.lang.String").classLoader ?: "null"}")
        line("android.os.Build.classLoader=${loaderA.loadClass("android.os.Build").classLoader ?: "null"}")

        val wrong = try {
            loaderA.loadClass("com.example.appsandbox.testguest.DoesNotExist")
            "INVALID: FOUND"
        } catch (error: ClassNotFoundException) {
            "ClassNotFoundException"
        }
        line("wrongClass=$wrong")

        val corrupt = File(activity.cacheDir, "exp001-corrupt.apk")
        corrupt.writeBytes(byteArrayOf(0x45, 0x58, 0x50, 0x30, 0x30, 0x31))
        val corruptResult = try {
            createLoader(activity, corrupt).loadClass(guestClassName)
            "INVALID: FOUND"
        } catch (error: Throwable) {
            "${error.javaClass.name}: ${error.message ?: "no message"}"
        } finally {
            corrupt.delete()
        }
        line("corruptApk=$corruptResult")
        check(Regex("EXP001_GUEST_[0-9a-f-]{36}").containsMatchIn(resultA.value)) {
            "Guest marker missing"
        }
        line("conclusion=CONFIRMED")
        return lines.joinToString("\n")
    }

    private fun createLoader(activity: Activity, apk: File): DexClassLoader {
        val optimized = File(activity.codeCacheDir, "exp001").apply { mkdirs() }
        return DexClassLoader(
            apk.absolutePath,
            optimized.absolutePath,
            null,
            Exp001Runner::class.java.classLoader
        )
    }

    private fun invokeProbe(loader: DexClassLoader, lines: MutableList<String>, label: String): Invocation {
        lines += "guestLoader$label=${loader.javaClass.name}"
        lines += "guestLoaderToString$label=$loader"
        lines += "guestParent$label=${loader.parent?.javaClass?.name ?: "null"}"
        lines += "guestParentToString$label=${loader.parent ?: "null"}"
        lines += "guestLibrarySearchPath$label=null"
        val clazz = loader.loadClass(guestClassName)
        check(clazz.classLoader === loader) { "GuestProbe was not defined by GuestLoader$label" }
        val instance = clazz.getDeclaredConstructor().newInstance()
        val method = clazz.getDeclaredMethod("ping", String::class.java)
        val value = method.invoke(instance, "exp001-$label") as String
        lines += "loadedClass$label=${clazz.name}"
        lines += "clazzLoaderMatches$label=${clazz.classLoader === loader}"
        lines += "constructor$label=OK"
        lines += "methodLookup$label=${method.name}"
        lines += "invocation$label=$value"
        lines += "guestHelper$label=RESOLVED"
        return Invocation(clazz, value)
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean = try {
        activity.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Invocation(val clazz: Class<*>, val value: String)
}
