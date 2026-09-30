package com.example.appsandbox.runtime

import android.util.Log
import dalvik.system.DexClassLoader

enum class ClassLoadingDomain { SYSTEM, RUNTIME_BRIDGE, GUEST }

class GuestDomainClassLoader(
    private val guestDexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    private val hostLoader: ClassLoader
) : DexClassLoader(guestDexPath, optimizedDirectory, librarySearchPath, hostLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name)?.let { return it }
        val domain = domainOf(name)
        val loaded = when (domain) {
            ClassLoadingDomain.SYSTEM, ClassLoadingDomain.RUNTIME_BRIDGE -> hostLoader.loadClass(name)
            ClassLoadingDomain.GUEST -> runCatching { findClass(name) }.getOrElse { hostLoader.loadClass(name) }
        }
        if (resolve) resolveClass(loaded)
        if (com.example.appsandbox.BuildConfig.DEBUG && shouldDiagnose(name)) {
            val source = if (loaded.classLoader === this) guestDexPath
                else loaded.protectionDomain?.codeSource?.location?.toString() ?: "boot/system"
            Log.i(TAG, "CLASSLOAD class=$name domain=$domain loader=${loaded.classLoader} source=$source " +
                "decision=${if (domain == ClassLoadingDomain.GUEST && loaded.classLoader === this) "guest-first" else "parent"}")
        }
        loaded
    }

    companion object {
        private const val TAG = "AppSandbox.M3"
        private val systemPrefixes = arrayOf(
            "java.", "javax.", "android.", "dalvik.", "org.xml.", "org.w3c.", "org.json.", "sun."
        )
        private val runtimePrefixes = arrayOf(
            "com.example.appsandbox.runtime.",
            "com.example.appsandbox.platform.",
            "com.example.appsandbox.identity.",
            "com.example.appsandbox.virtual.",
            "com.example.appsandbox.vpm."
        )

        fun domainOf(name: String): ClassLoadingDomain = when {
            systemPrefixes.any(name::startsWith) -> ClassLoadingDomain.SYSTEM
            runtimePrefixes.any(name::startsWith) -> ClassLoadingDomain.RUNTIME_BRIDGE
            else -> ClassLoadingDomain.GUEST
        }

        private fun shouldDiagnose(name: String) =
            name.startsWith("androidx.") || name.startsWith("kotlin.") || name.contains("zeroadapt") || name.contains("mahoshojo")
    }
}
